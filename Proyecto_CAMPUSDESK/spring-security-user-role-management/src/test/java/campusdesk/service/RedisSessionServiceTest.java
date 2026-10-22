package campusdesk.service;

import campusdesk.dto.UserSessionRedisDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisSessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    private ObjectMapper objectMapper;

    private RedisSessionServiceImpl redisSessionService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        redisSessionService = new RedisSessionServiceImpl(redisTemplate, stringRedisTemplate, objectMapper);
    }

    @Test
    @DisplayName("saveSession debe persistir la sesión con TTL estricto e indexar por usuario")
    void testSaveSession() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);

        UserSessionRedisDto sessionDto = UserSessionRedisDto.builder()
                .userId(1L)
                .username("superadmin@campusdesk.com")
                .email("superadmin@campusdesk.com")
                .role("ADMIN")
                .authorities(List.of("ROLE_ADMIN"))
                .build();

        String token = "jwt.test.token";
        long ttlSeconds = 28800L;

        redisSessionService.saveSession(token, sessionDto, ttlSeconds);

        verify(valueOperations, times(1)).set(startsWith("campusdesk:session:"), eq(sessionDto), eq(Duration.ofSeconds(ttlSeconds)));
        verify(setOperations, times(1)).add(eq("campusdesk:user:active_tokens:superadmin@campusdesk.com"), anyString());
    }

    @Test
    @DisplayName("getSession debe retornar Optional con datos cuando la clave existe")
    void testGetSessionFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        UserSessionRedisDto expected = UserSessionRedisDto.builder()
                .userId(1L)
                .username("superadmin@campusdesk.com")
                .email("superadmin@campusdesk.com")
                .role("ADMIN")
                .build();

        when(valueOperations.get(startsWith("campusdesk:session:"))).thenReturn(expected);

        Optional<UserSessionRedisDto> result = redisSessionService.getSession("jwt.test.token");

        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("superadmin@campusdesk.com");
    }

    @Test
    @DisplayName("getSession debe retornar Optional.empty() cuando la clave no existe o expiró")
    void testGetSessionNotFound() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        Optional<UserSessionRedisDto> result = redisSessionService.getSession("jwt.expired.token");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("invalidateSession debe eliminar la clave de sesión en Redis")
    void testInvalidateSession() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.delete(startsWith("campusdesk:session:"))).thenReturn(Boolean.TRUE);

        redisSessionService.invalidateSession("jwt.to.delete");

        verify(redisTemplate, times(1)).delete(startsWith("campusdesk:session:"));
    }

    @Test
    @DisplayName("invalidateAllSessionsByUser debe revocar todos los tokens del usuario")
    void testInvalidateAllSessionsByUser() {
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("campusdesk:user:active_tokens:user1")).thenReturn(Set.of("hash1", "hash2"));

        redisSessionService.invalidateAllSessionsByUser("user1");

        verify(redisTemplate, times(1)).delete("campusdesk:session:hash1");
        verify(redisTemplate, times(1)).delete("campusdesk:session:hash2");
        verify(stringRedisTemplate, times(1)).delete("campusdesk:user:active_tokens:user1");
    }
}
