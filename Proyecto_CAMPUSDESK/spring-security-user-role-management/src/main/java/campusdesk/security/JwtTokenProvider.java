package campusdesk.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

/**
 * Componente para la generación, firmado y validación de tokens JWT.
 */
@Component
public class JwtTokenProvider {

    private final String issuer;
    private final long expirationTimeInMs;
    private final Algorithm algorithm;
    private final JWTVerifier verifier;

    public JwtTokenProvider(
            @Value("${security.jwt.secret:}") String secretKey,
            @Value("${security.jwt.issuer:CampusDesk_API}") String issuer,
            @Value("${security.jwt.expiration-ms:86400000}") long expirationTimeInMs
    ) {
        if (secretKey == null || secretKey.trim().length() < 32) {
            throw new IllegalStateException("JWT_SECRET must be configured and contain at least 32 characters");
        }
        this.issuer = issuer;
        this.expirationTimeInMs = expirationTimeInMs;
        this.algorithm = Algorithm.HMAC256(secretKey.trim());
        this.verifier = JWT.require(algorithm).withIssuer(issuer).build();
    }


    /**
     * Genera un token JWT incluyendo subject (email) y claim del rol.
     *
     * @param username Correo o nombre del usuario.
     * @param role Rol del usuario.
     * @return Token JWT firmado.
     */
    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return JWT.create()
                .withSubject(username)
                .withIssuer(issuer)
                .withClaim("role", role)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusMillis(expirationTimeInMs)))
                .sign(algorithm);
    }

    /**
     * Valida el token JWT.
     *
     * @param token Token JWT a verificar.
     * @return true si es válido, false en caso contrario.
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            verifier.verify(token);
            return true;
        } catch (JWTVerificationException e) {
            return false;
        }
    }

    /**
     * Obtiene el subject (email/username) del token JWT.
     */
    public String getUsernameFromToken(String token) {
        return verifier.verify(token).getSubject();
    }

    /**
     * Obtiene el rol almacenado en los claims del token JWT.
     */
    public String getRoleFromToken(String token) {
        return verifier.verify(token).getClaim("role").asString();
    }
}
