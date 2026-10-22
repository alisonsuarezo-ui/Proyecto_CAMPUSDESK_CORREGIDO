package campusdesk.service;

import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.User;
import campusdesk.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("registerUser debe encriptar la contraseña y asignar el rol USER por defecto")
    void testRegisterUserAssignsUserRoleAndEncodesPassword() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Maria Fernandez")
                .email("maria@campusdesk.com")
                .password("RawPassword123")
                .build();

        when(userRepository.existsByEmail("maria@campusdesk.com")).thenReturn(false);
        when(passwordEncoder.encode("RawPassword123")).thenReturn("$2a$10$encodedHash");

        User savedUser = User.builder()
                .id(10L)
                .name("Maria Fernandez")
                .email("maria@campusdesk.com")
                .password("$2a$10$encodedHash")
                .role(Role.USER)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserResponseDTO response = userService.registerUser(dto);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("Maria Fernandez");
        assertThat(response.getEmail()).isEqualTo("maria@campusdesk.com");
        assertThat(response.getRole()).isEqualTo(Role.USER);

        verify(passwordEncoder, times(1)).encode("RawPassword123");
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    @DisplayName("registerUser con email duplicado debe lanzar IllegalArgumentException")
    void testRegisterUserDuplicateEmailThrowsException() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Maria Fernandez")
                .email("maria@campusdesk.com")
                .password("RawPassword123")
                .build();

        when(userRepository.existsByEmail("maria@campusdesk.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already registered");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("findByEmail retorna el usuario cuando existe")
    void testFindByEmailReturnsUser() {
        User user = User.builder()
                .id(1L)
                .email("test@campusdesk.com")
                .role(Role.ADMIN)
                .build();

        when(userRepository.findByEmail("test@campusdesk.com")).thenReturn(Optional.of(user));

        Optional<User> result = userService.findByEmail("test@campusdesk.com");

        assertThat(result).isPresent();
        assertThat(result.get().getRole()).isEqualTo(Role.ADMIN);
    }
}
