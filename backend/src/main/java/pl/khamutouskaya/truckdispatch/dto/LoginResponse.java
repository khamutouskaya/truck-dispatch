package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.Role;

import java.time.Instant;

/**
 * Wynik logowania. React zapisuje token i wysyła go w nagłówku {@code Authorization: Bearer <accessToken>}.
 *
 * @param accessToken JWT
 * @param tokenType   zawsze "Bearer"
 * @param expiresAt   koniec ważności tokenu (UTC) — po nim trzeba zalogować się ponownie
 * @param role        rola — React wybiera ekran (dyspozytor / panel kierowcy)
 * @param driverId    kierowca konta (tylko rola DRIVER, inaczej null)
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        String username,
        Role role,
        Long driverId
) {
}
