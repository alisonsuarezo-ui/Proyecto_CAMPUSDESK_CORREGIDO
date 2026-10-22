package campusdesk.controller;

import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.User;
import campusdesk.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private Principal adminPrincipal;
    private Principal userPrincipal;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController).build();
        adminPrincipal = () -> "superadmin@campusdesk.com";
        userPrincipal = () -> "carlos@campusdesk.com";
    }

    @Test
    @DisplayName("F2: GET /api/users retorna lista de todos los usuarios para ADMIN sin contraseñas ni hashes")
    void testGetAllUsersReturnsSafeDTOList() throws Exception {
        UserResponseDTO u1 = UserResponseDTO.builder().id(1L).name("Admin").email("admin@test.com").role(Role.ADMIN).build();
        UserResponseDTO u2 = UserResponseDTO.builder().id(2L).name("Tech").email("tech@test.com").role(Role.TECHNICIAN).build();
        UserResponseDTO u3 = UserResponseDTO.builder().id(3L).name("User").email("user@test.com").role(Role.USER).build();

        when(userService.getAllUsers()).thenReturn(List.of(u1, u2, u3));

        mockMvc.perform(get("/api/users").principal(adminPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("Admin"))
                .andExpect(jsonPath("$[0].email").value("admin@test.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[1].password").doesNotExist())
                .andExpect(jsonPath("$[2].password").doesNotExist());
    }

    @Test
    @DisplayName("E4: GET /api/users/technicians retorna técnicos disponibles para ADMIN")
    void testGetTechniciansReturnsOnlyTechnicians() throws Exception {
        UserResponseDTO t1 = UserResponseDTO.builder().id(2L).name("Ana Tech").email("ana@test.com").role(Role.TECHNICIAN).build();

        when(userService.getTechnicians()).thenReturn(List.of(t1));

        mockMvc.perform(get("/api/users/technicians").principal(adminPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].role").value("TECHNICIAN"))
                .andExpect(jsonPath("$[0].password").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/users/me retorna perfil de usuario autenticado")
    void testGetMyProfile() throws Exception {
        User user = User.builder().id(3L).name("Carlos Gomez").email("carlos@campusdesk.com").role(Role.USER).build();
        when(userService.findByEmail("carlos@campusdesk.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/users/me").principal(userPrincipal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("carlos@campusdesk.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
