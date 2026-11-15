package pl.khamutouskaya.truckdispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Ustawienia tokenów JWT (application.yaml, jwt.*).
 *
 * @param secret klucz podpisu HMAC-SHA256; co najmniej 32 bajty (256 bitów) — wymóg algorytmu HS256
 * @param ttl    czas ważności tokenu
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration ttl) {

    public static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("jwt.secret must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("jwt.ttl must be positive");
        }
    }
}
