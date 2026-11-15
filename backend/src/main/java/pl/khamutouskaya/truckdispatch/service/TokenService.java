package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pl.khamutouskaya.truckdispatch.config.JwtProperties;
import pl.khamutouskaya.truckdispatch.domain.AppUser;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Wydawanie tokenów JWT. Zawartość (claims):
 * <ul>
 *   <li>{@code iss} — "truckdispatch", {@code sub} — login, {@code iat}/{@code exp} — wydanie i koniec ważności;</li>
 *   <li>{@code role} — DISPATCHER / DRIVER (reguły dostępu);</li>
 *   <li>{@code driverId} — tylko dla kierowcy: „moje” dane bierzemy z tokenu, nie z adresu.</li>
 * </ul>
 * Token jest podpisany, ale NIE zaszyfrowany — nie wolno w nim trzymać tajemnic (np. hasła).
 */
@Service
@RequiredArgsConstructor
public class TokenService {

    public static final String ISSUER = "truckdispatch";
    public static final String ROLE_CLAIM = "role";
    public static final String DRIVER_ID_CLAIM = "driverId";

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issue(AppUser user) {
        // JWT zapisuje czas w pełnych sekundach — obcinamy, żeby expiresAt w odpowiedzi = exp w tokenie
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = now.plus(properties.ttl());
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(user.getUsername())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(ROLE_CLAIM, user.getRole().name());
        if (user.getDriver() != null) {
            claims.claim(DRIVER_ID_CLAIM, user.getDriver().getId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
