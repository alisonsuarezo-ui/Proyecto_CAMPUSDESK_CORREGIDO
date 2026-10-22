package campusdesk.service;

import campusdesk.dto.UserSessionRedisDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * Implementación de RedisSessionService utilizando RedisTemplate y StringRedisTemplate.
 * Implementa una política estricta de TTL y llaves namespaced.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSessionServiceImpl implements RedisSessionService {

    private static final String SESSION_KEY_PREFIX = "campusdesk:session:";
    private static final String USER_TOKENS_KEY_PREFIX = "campusdesk:user:active_tokens:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void saveSession(String token, UserSessionRedisDto sessionData, long ttlSeconds) {
        if (token == null || sessionData == null) {
            log.warn("Intento de guardar sesión con token o datos nulos");
            return;
        }

        String tokenHash = hashToken(token);
        String sessionKey = SESSION_KEY_PREFIX + tokenHash;
        Duration ttl = Duration.ofSeconds(Math.max(ttlSeconds, 1));

        // 1. Guardar la sesión completa con TTL estricto
        redisTemplate.opsForValue().set(sessionKey, sessionData, ttl);
        log.info("Sesión guardada en Redis con clave [{}] y TTL de {} segundos para el usuario [{}]",
                sessionKey, ttlSeconds, sessionData.getUsername());

        // 2. Indexar token por usuario en Set para control multi-sesión
        String username = sessionData.getUsername() != null ? sessionData.getUsername() : sessionData.getEmail();
        if (username != null && !username.isBlank()) {
            String userTokensKey = USER_TOKENS_KEY_PREFIX + username;
            stringRedisTemplate.opsForSet().add(userTokensKey, tokenHash);
            stringRedisTemplate.expire(userTokensKey, ttl);
        }
    }

    @Override
    public Optional<UserSessionRedisDto> getSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String tokenHash = hashToken(token);
        String sessionKey = SESSION_KEY_PREFIX + tokenHash;

        Object rawSession = redisTemplate.opsForValue().get(sessionKey);
        if (rawSession == null) {
            log.debug("No se encontró sesión activa en Redis para la clave [{}]", sessionKey);
            return Optional.empty();
        }

        try {
            UserSessionRedisDto sessionDto;
            if (rawSession instanceof UserSessionRedisDto typed) {
                sessionDto = typed;
            } else {
                sessionDto = objectMapper.convertValue(rawSession, UserSessionRedisDto.class);
            }
            return Optional.of(sessionDto);
        } catch (Exception e) {
            log.error("Error al deserializar sesión desde Redis para clave [{}]: {}", sessionKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isSessionActive(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String sessionKey = SESSION_KEY_PREFIX + hashToken(token);
        Boolean hasKey = redisTemplate.hasKey(sessionKey);
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void invalidateSession(String token) {
        if (token == null || token.isBlank()) {
            return;
        }

        String tokenHash = hashToken(token);
        String sessionKey = SESSION_KEY_PREFIX + tokenHash;

        // Recuperar sesión antes de borrar para limpiar el índice del usuario
        Optional<UserSessionRedisDto> sessionOpt = getSession(token);
        sessionOpt.ifPresent(session -> {
            String username = session.getUsername() != null ? session.getUsername() : session.getEmail();
            if (username != null && !username.isBlank()) {
                String userTokensKey = USER_TOKENS_KEY_PREFIX + username;
                stringRedisTemplate.opsForSet().remove(userTokensKey, tokenHash);
            }
        });

        // Eliminar clave de sesión
        Boolean deleted = redisTemplate.delete(sessionKey);
        log.info("Sesión invalidada en Redis para clave [{}] (eliminada: {})", sessionKey, deleted);
    }

    @Override
    public void invalidateAllSessionsByUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }

        String userTokensKey = USER_TOKENS_KEY_PREFIX + username;
        Set<String> tokenHashes = stringRedisTemplate.opsForSet().members(userTokensKey);

        if (tokenHashes != null && !tokenHashes.isEmpty()) {
            for (String hash : tokenHashes) {
                String sessionKey = SESSION_KEY_PREFIX + hash;
                redisTemplate.delete(sessionKey);
                log.info("Sesión revocada [{}] para el usuario [{}]", sessionKey, username);
            }
            stringRedisTemplate.delete(userTokensKey);
        }
        log.info("Todas las sesiones revocadas para el usuario [{}]", username);
    }

    @Override
    public void refreshSession(String token, long ttlSeconds) {
        if (token == null || token.isBlank()) {
            return;
        }
        String sessionKey = SESSION_KEY_PREFIX + hashToken(token);
        redisTemplate.expire(sessionKey, Duration.ofSeconds(Math.max(ttlSeconds, 1)));
    }

    /**
     * Genera un hash SHA-256 del token para usar como clave fija y segura en Redis.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedHash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encodedHash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("Algoritmo SHA-256 no disponible, usando hashCode", e);
            return Integer.toHexString(token.hashCode());
        }
    }
}
