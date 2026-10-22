package campusdesk.controller;

import campusdesk.dto.LoginRequestDTO;
import campusdesk.dto.LoginResponseDTO;
import campusdesk.dto.UserRegistrationDTO;
import campusdesk.dto.UserResponseDTO;
import campusdesk.dto.UserSessionRedisDto;
import campusdesk.entity.User;
import campusdesk.security.JwtTokenProvider;
import campusdesk.service.RedisSessionService;
import campusdesk.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controlador REST para autenticación, gestión de sesiones y revocación de tokens.
 * Mapea tanto a /auth como a /api/auth para compatibilidad total con frontend y API REST.
 */
@RestController
@RequestMapping({"/api/auth", "/auth"})
@Slf4j
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisSessionService redisSessionService;
    private final long sessionTtlSeconds;

    public AuthController(
            UserService userService,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RedisSessionService redisSessionService,
            @Value("${security.session.redis.ttl-seconds:28800}") long sessionTtlSeconds
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.redisSessionService = redisSessionService;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    /**
     * Endpoint público para registrar usuarios (/auth/register o /api/auth/register).
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody UserRegistrationDTO registrationDTO) {
        UserResponseDTO response = userService.registerUser(registrationDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Endpoint público para iniciar sesión (/auth/login o /api/auth/login).
     * Tras verificar credenciales, emite el JWT y persiste la sesión completa en Redis con TTL estricto.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequestDTO loginDTO, HttpServletRequest request) {
        User user = userService.findByEmail(loginDTO.getEmail()).orElse(null);

        // Validación de existencia y coincidencia de contraseña cifrada con BCrypt
        if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized", "message", "Invalid credentials"));
        }

        // Generar token JWT incorporando el subject y el claim del rol
        String token = jwtTokenProvider.generateToken(user.getEmail(), user.getRole().name());

        // Cargar lista completa de autoridades y permisos según el rol asignado
        List<String> authorities = switch (user.getRole()) {
            case ADMIN -> List.of(
                    "ROLE_ADMIN",
                    "TICKET_READ",
                    "TICKET_CREATE",
                    "TICKET_UPDATE",
                    "TICKET_DELETE",
                    "TICKET_COMMENT",
                    "TICKET_HISTORY_READ",
                    "USER_MANAGE"
            );
            case TECHNICIAN -> List.of(
                    "ROLE_TECHNICIAN",
                    "TICKET_READ",
                    "TICKET_UPDATE",
                    "TICKET_COMMENT",
                    "TICKET_HISTORY_READ"
            );
            case USER -> List.of(
                    "ROLE_USER",
                    "TICKET_READ",
                    "TICKET_CREATE",
                    "TICKET_COMMENT",
                    "TICKET_HISTORY_READ"
            );
        };

        // Extraer IP del cliente y User-Agent para auditoría
        String clientIp = request.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isBlank()) {
            clientIp = request.getRemoteAddr();
        } else if (clientIp.contains(",")) {
            clientIp = clientIp.split(",")[0].trim();
        }
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(sessionTtlSeconds);

        // Construir DTO de sesión para Redis
        UserSessionRedisDto sessionData = UserSessionRedisDto.builder()
                .userId(user.getId())
                .username(user.getEmail())
                .email(user.getEmail())
                .role(user.getRole().name())
                .authorities(authorities)
                .token(token)
                .loginTimestamp(now.toString())
                .expiresAt(expiresAt.toString())
                .ttlSeconds(sessionTtlSeconds)
                .ipAddress(clientIp != null ? clientIp : "127.0.0.1")
                .userAgent(userAgent != null ? userAgent : "Unknown")
                .build();

        // Persistir sesión en memoria con TTL estricto en Redis
        redisSessionService.saveSession(token, sessionData, sessionTtlSeconds);

        return ResponseEntity.ok(LoginResponseDTO.builder()
                .token(token)
                .type("Bearer")
                .role(user.getRole().name())
                .build());
    }

    /**
     * Endpoint protegido para revocación de sesión inmediata (/auth/logout o /api/auth/logout).
     * Elimina la clave de sesión de Redis invalidando el token en tiempo real.
     */
    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            redisSessionService.invalidateSession(token);
            log.info("Logout procesado exitosamente para token Bearer");
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(Map.of("message", "Sesión cerrada exitosamente"));
    }

    /**
     * Endpoint protegido para consultar la sesión actual (/auth/me o /api/auth/me).
     * Lee directamente desde la caché caliente de Redis sin realizar consultas a PostgreSQL.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<UserSessionRedisDto> sessionOpt = redisSessionService.getSession(token);
            if (sessionOpt.isPresent()) {
                return ResponseEntity.ok(sessionOpt.get());
            }
        }

        String principal = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(Map.of("username", principal));
    }
}
