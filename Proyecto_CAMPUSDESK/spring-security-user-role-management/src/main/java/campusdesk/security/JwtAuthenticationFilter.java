package campusdesk.security;

import campusdesk.dto.ApiErrorResponse;
import campusdesk.dto.UserSessionRedisDto;
import campusdesk.service.RedisSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Filtro interceptor por petición que extrae, valida criptográficamente el JWT
 * y verifica la existencia de la sesión activa en el almacenamiento distribuido Redis.
 * Carga el contexto de seguridad directamente desde la caché caliente sin consultar PostgreSQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisSessionService redisSessionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        // Si la petición contiene cabecera de autorización Bearer
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();

            if (!token.isEmpty()) {
                // 1. Validar firma criptográfica y vigencia matemática del JWT
                if (!jwtTokenProvider.validateToken(token)) {
                    log.warn("Petición rechazada: Token JWT con firma o expiración inválida en [{}]", request.getRequestURI());
                    sendUnauthorizedResponse(response, request, "Token JWT inválido o expirado");
                    return;
                }

                // 2. Comprobar existencia de la sesión en Redis (Verificación de revocación / logout en tiempo real)
                Optional<UserSessionRedisDto> sessionOpt = redisSessionService.getSession(token);

                if (sessionOpt.isEmpty()) {
                    log.warn("Petición rechazada: Sesión no encontrada en Redis (revocada o expirada) para token en [{}]",
                            request.getRequestURI());
                    sendUnauthorizedResponse(response, request, "Sesión inválida, expirada o revocada por el usuario");
                    return;
                }

                // 3. Establecer Authentication en SecurityContextHolder usando la caché caliente de Redis (CERO SQL)
                UserSessionRedisDto session = sessionOpt.get();

                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    List<SimpleGrantedAuthority> authorities;
                    if (session.getAuthorities() != null && !session.getAuthorities().isEmpty()) {
                        authorities = session.getAuthorities().stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();
                    } else if (session.getRole() != null) {
                        authorities = List.of(new SimpleGrantedAuthority("ROLE_" + session.getRole()));
                    } else {
                        authorities = List.of();
                    }

                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(session.getUsername(), null, authorities);

                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    log.debug("Usuario [{}] autenticado exitosamente desde caché Redis", session.getUsername());
                }
            }
        }

        // Continuar cadena de filtros
        filterChain.doFilter(request, response);
    }

    /**
     * Escribe la respuesta JSON estructurada 401 Unauthorized ante tokens o sesiones inválidas.
     */
    private void sendUnauthorizedResponse(HttpServletResponse response, HttpServletRequest request, String message)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiErrorResponse error = ApiErrorResponse.builder()
                .status(HttpServletResponse.SC_UNAUTHORIZED)
                .error("Unauthorized")
                .message(message)
                .timestamp(Instant.now().toString())
                .path(request.getRequestURI())
                .build();

        objectMapper.writeValue(response.getOutputStream(), error);
    }
}
