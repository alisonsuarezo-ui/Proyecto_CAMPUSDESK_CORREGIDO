package campusdesk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * DTO que representa la sesión activa de un usuario en el almacenamiento distribuido Redis.
 * Permite mantener el contexto de seguridad y caché caliente sin consultar PostgreSQL repetitivamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserSessionRedisDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;

    private String username;

    private String email;

    private String role;

    private List<String> authorities;

    private String token;

    private String loginTimestamp;

    private String expiresAt;

    private Long ttlSeconds;

    private String ipAddress;

    private String userAgent;
}
