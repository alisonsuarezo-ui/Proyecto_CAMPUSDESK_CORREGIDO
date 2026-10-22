package campusdesk.service;

import campusdesk.dto.UserSessionRedisDto;

import java.util.Optional;

/**
 * Contrato de servicio para la gestión distribuida de sesiones y revocación de tokens en Redis.
 */
public interface RedisSessionService {

    /**
     * Guarda la sesión del usuario con un TTL estricto sincronizado con la expiración del JWT.
     *
     * @param token Token JWT emitido.
     * @param sessionData Datos completos y cacheados de la sesión.
     * @param ttlSeconds Tiempo de vida en segundos.
     */
    void saveSession(String token, UserSessionRedisDto sessionData, long ttlSeconds);

    /**
     * Obtiene los datos de la sesión activa a partir del token JWT.
     *
     * @param token Token JWT.
     * @return Optional con el UserSessionRedisDto si existe y no ha expirado.
     */
    Optional<UserSessionRedisDto> getSession(String token);

    /**
     * Verifica si una sesión asociada al token existe y continúa activa en Redis.
     *
     * @param token Token JWT.
     * @return true si la sesión está activa, false en caso contrario.
     */
    boolean isSessionActive(String token);

    /**
     * Invalida y elimina la sesión activa de Redis (Logout inmediato).
     *
     * @param token Token JWT a revocar.
     */
    void invalidateSession(String token);

    /**
     * Invalida todas las sesiones asociadas a un nombre de usuario (ej. reseteo o bloqueo).
     *
     * @param username Correo o nombre del usuario.
     */
    void invalidateAllSessionsByUser(String username);

    /**
     * Extiende el TTL de la sesión para soportar mecanismos de sesión deslizante.
     *
     * @param token Token JWT.
     * @param ttlSeconds Nuevo tiempo de vida en segundos.
     */
    void refreshSession(String token, long ttlSeconds);
}
