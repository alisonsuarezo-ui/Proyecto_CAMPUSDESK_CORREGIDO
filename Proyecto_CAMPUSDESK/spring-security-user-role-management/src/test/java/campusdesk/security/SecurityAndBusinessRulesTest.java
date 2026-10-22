package campusdesk.security;

import campusdesk.dto.UpdateTicketDTO;
import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.entity.Role;
import campusdesk.entity.TicketCategory;
import campusdesk.entity.TicketPriority;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias de seguridad y validación de reglas de negocio para CAMPUSDESK (Sección 6).
 */
class SecurityAndBusinessRulesTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // =========================================================================
    // 1. POLÍTICA DE CONTRASEÑAS (F3)
    // =========================================================================

    @Test
    @DisplayName("F3: Contraseña válida cumple con la política (8-100 caracteres, mayúscula, minúscula y número)")
    void testValidPasswordPassesValidation() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Estudiante Prueba")
                .email("estudiante@campusdesk.com")
                .password("Password123!")
                .build();

        Set<ConstraintViolation<UserRegistrationDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("F3: Contraseña débil sin mayúscula es rechazada por el backend")
    void testPasswordWithoutUppercaseFails() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Estudiante Prueba")
                .email("estudiante@campusdesk.com")
                .password("password123!")
                .build();

        Set<ConstraintViolation<UserRegistrationDTO>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("F3: Contraseña débil sin minúscula es rechazada por el backend")
    void testPasswordWithoutLowercaseFails() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Estudiante Prueba")
                .email("estudiante@campusdesk.com")
                .password("PASSWORD123!")
                .build();

        Set<ConstraintViolation<UserRegistrationDTO>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("F3: Contraseña débil sin número es rechazada por el backend")
    void testPasswordWithoutDigitFails() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Estudiante Prueba")
                .email("estudiante@campusdesk.com")
                .password("PasswordSinNumero!")
                .build();

        Set<ConstraintViolation<UserRegistrationDTO>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("F3: Contraseña menor a 8 caracteres es rechazada por el backend")
    void testPasswordTooShortFails() {
        UserRegistrationDTO dto = UserRegistrationDTO.builder()
                .name("Estudiante Prueba")
                .email("estudiante@campusdesk.com")
                .password("P1a!")
                .build();

        Set<ConstraintViolation<UserRegistrationDTO>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    // =========================================================================
    // 2. GESTIÓN SEGURA DE SECRETOS JWT (E6)
    // =========================================================================

    @Test
    @DisplayName("E6: JwtTokenProvider inicializa correctamente con clave de 32 o más caracteres")
    void testJwtTokenProviderInitializesWithValidSecret() {
        String validSecret = "CampusDesk_Secret_Key_2026_Secure_Token_Min32Chars!";
        assertDoesNotThrow(() -> new JwtTokenProvider(validSecret, "CampusDesk_API", 86400000L));
    }

    @Test
    @DisplayName("E6: JwtTokenProvider falla al arrancar si falta JWT_SECRET (clave nula o vacía)")
    void testJwtTokenProviderFailsWithEmptySecret() {
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider("", "CampusDesk_API", 86400000L));
        assertThrows(IllegalStateException.class, () -> new JwtTokenProvider(null, "CampusDesk_API", 86400000L));
    }

    @Test
    @DisplayName("E6: JwtTokenProvider falla al arrancar si JWT_SECRET tiene menos de 32 caracteres")
    void testJwtTokenProviderFailsWithShortSecret() {
        String shortSecret = "Short_Secret_Key_12345";
        assertThat(shortSecret.length()).isLessThan(32);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(shortSecret, "CampusDesk_API", 86400000L));

        assertThat(ex.getMessage()).contains("at least 32 characters");
    }

    // =========================================================================
    // 3. CONFIGURACIÓN CORS (E5)
    // =========================================================================

    @Test
    @DisplayName("E5: CORS valida orígenes permitidos explícitos y rechaza comodín con credenciales")
    void testCorsConfigurationSource() {
        SecurityConfig config = new SecurityConfig(null);
        ReflectionTestUtils.setField(config, "allowedOrigins", List.of("http://localhost:5506", "http://localhost:5500"));

        CorsConfigurationSource source = config.corsConfigurationSource();
        assertThat(source).isNotNull();

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/tickets");

        CorsConfiguration corsConfig = source.getCorsConfiguration(request);
        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOrigins()).containsExactlyInAnyOrder("http://localhost:5506", "http://localhost:5500");
        assertThat(corsConfig.getAllowCredentials()).isTrue();
        assertThat(corsConfig.getAllowedOriginPatterns()).isNull(); // No debe tener comodines con credenciales
    }

    // =========================================================================
    // 4. SEGURIDAD DE DTOs DE USUARIO (F2)
    // =========================================================================

    @Test
    @DisplayName("F2: UserResponseDTO nunca contiene campos de contraseñas ni hashes BCrypt")
    void testUserResponseDTODoesNotExposePasswordOrHash() {
        Field[] fields = UserResponseDTO.class.getDeclaredFields();
        List<String> fieldNames = Arrays.stream(fields).map(Field::getName).toList();

        assertThat(fieldNames).doesNotContain("password", "passwd", "hash", "salt", "secret");
        assertThat(fieldNames).contains("id", "name", "email", "role");
    }

    // =========================================================================
    // 5. VALIDACIÓN DE CAMPOS EN UpdateTicketDTO (F1)
    // =========================================================================

    @Test
    @DisplayName("F1: UpdateTicketDTO es válido con campos correctos")
    void testUpdateTicketDTOValid() {
        UpdateTicketDTO dto = UpdateTicketDTO.builder()
                .title("Título actualizado")
                .description("Descripción suficientemente explicativa del problema")
                .priority(TicketPriority.ALTA)
                .category(TicketCategory.HARDWARE)
                .build();

        Set<ConstraintViolation<UpdateTicketDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("F1: UpdateTicketDTO rechaza título vacío")
    void testUpdateTicketDTOBlankTitleFails() {
        UpdateTicketDTO dto = UpdateTicketDTO.builder()
                .title("")
                .description("Descripción del problema")
                .priority(TicketPriority.MEDIA)
                .category(TicketCategory.SOFTWARE)
                .build();

        Set<ConstraintViolation<UpdateTicketDTO>> violations = validator.validate(dto);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("title"));
    }
}
