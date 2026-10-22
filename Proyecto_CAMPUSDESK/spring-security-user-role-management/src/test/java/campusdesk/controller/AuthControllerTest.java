package campusdesk.controller;

import campusdesk.dto.LoginRequestDTO;
import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.dto.UserSessionRedisDto;
import campusdesk.entity.Role;
import campusdesk.entity.User;
import campusdesk.security.JwtTokenProvider;
import campusdesk.service.RedisSessionService;
import campusdesk.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserService userService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RedisSessionService redisSessionService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(userService, passwordEncoder, jwtTokenProvider, redisSessionService, 28800L);
        mockMvc = MockMvcBuilders.standaloneSetup(authController).build();
    }

    @Test
    @DisplayName("POST /auth/register - Exitoso debe retornar 201 y UserResponseDTO con rol USER")
    void testRegisterSuccess() throws Exception {
        UserRegistrationDTO request = UserRegistrationDTO.builder()
                .name("Carlos Gomez")
                .email("carlos@campusdesk.com")
                .password("Password123!")
                .build();

        UserResponseDTO responseDTO = UserResponseDTO.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos@campusdesk.com")
                .role(Role.USER)
                .build();

        when(userService.registerUser(any(UserRegistrationDTO.class))).thenReturn(responseDTO);

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Carlos Gomez"))
                .andExpect(jsonPath("$.email").value("carlos@campusdesk.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(userService, times(1)).registerUser(any(UserRegistrationDTO.class));
    }

    @Test
    @DisplayName("POST /auth/login - Exitoso debe retornar 200 con JWT y persistir sesión en Redis")
    void testLoginSuccess() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .email("carlos@campusdesk.com")
                .password("Password123!")
                .build();

        User mockUser = User.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos@campusdesk.com")
                .password("$2a$10$encodedPassword")
                .role(Role.USER)
                .build();

        when(userService.findByEmail("carlos@campusdesk.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("Password123!", "$2a$10$encodedPassword")).thenReturn(true);
        when(jwtTokenProvider.generateToken("carlos@campusdesk.com", "USER")).thenReturn("mocked.jwt.token");

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(redisSessionService, times(1)).saveSession(eq("mocked.jwt.token"), any(UserSessionRedisDto.class), anyLong());
    }

    @Test
    @DisplayName("POST /auth/login - Contraseña incorrecta debe retornar 401 Unauthorized")
    void testLoginInvalidPassword() throws Exception {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .email("carlos@campusdesk.com")
                .password("WrongPassword")
                .build();

        User mockUser = User.builder()
                .id(1L)
                .name("Carlos Gomez")
                .email("carlos@campusdesk.com")
                .password("$2a$10$encodedPassword")
                .role(Role.USER)
                .build();

        when(userService.findByEmail("carlos@campusdesk.com")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("WrongPassword", "$2a$10$encodedPassword")).thenReturn(false);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(redisSessionService, never()).saveSession(any(), any(), anyLong());
    }

    @Test
    @DisplayName("POST /auth/logout - Exitoso debe revocar la sesión en Redis y retornar 200")
    void testLogoutSuccess() throws Exception {
        String token = "sample.bearer.token";

        mockMvc.perform(post("/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sesión cerrada exitosamente"));

        verify(redisSessionService, times(1)).invalidateSession(token);
    }

    @Test
    @DisplayName("GET /auth/me - Exitoso debe recuperar sesión de la caché caliente de Redis")
    void testGetMeFromRedis() throws Exception {
        String token = "sample.bearer.token";
        UserSessionRedisDto sessionDto = UserSessionRedisDto.builder()
                .userId(1L)
                .username("carlos@campusdesk.com")
                .email("carlos@campusdesk.com")
                .role("USER")
                .authorities(List.of("ROLE_USER", "TICKET_READ"))
                .token(token)
                .build();

        when(redisSessionService.getSession(token)).thenReturn(Optional.of(sessionDto));

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("carlos@campusdesk.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        verify(redisSessionService, times(1)).getSession(token);
    }
}
