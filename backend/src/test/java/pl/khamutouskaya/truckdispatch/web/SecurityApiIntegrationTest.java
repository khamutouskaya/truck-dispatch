package pl.khamutouskaya.truckdispatch.web;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Zamknięte API: prawdziwe tokeny (z logowania i podrobione), bez podstawiania jwt().
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JwtEncoder jwtEncoder;

    private String loginToken() throws Exception {
        return loginToken("dyspozytor");
    }

    private String loginToken(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"haslo123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String sign(JwtEncoder encoder, Instant issuedAt, Duration ttl) {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("truckdispatch").subject("dyspozytor")
                .issuedAt(issuedAt).expiresAt(issuedAt.plus(ttl)).claim("role", "DISPATCHER").build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Test
    void publicEndpointsWorkWithoutToken() throws Exception {
        mockMvc.perform(get("/api/ping")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        // Logowanie jest otwarte (tu 401 z powodu hasła, a nie braku tokenu)
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"dyspozytor\", \"password\": \"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"));
    }

    @Test
    void requestWithoutTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/drivers"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value(
                        "Authentication required: send header Authorization: Bearer <token> (POST /api/auth/login)"));
    }

    @Test
    void tokenFromLoginOpensApi() throws Exception {
        String token = loginToken();
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void garbageTokenIs401() throws Exception {
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer("abc.def.ghi")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    void expiredTokenIs401() throws Exception {
        // Podpisany właściwym kluczem, ale wygasł 9 h temu (wydany 17 h temu, ważny 8 h)
        String expired = sign(jwtEncoder, Instant.now().minus(Duration.ofHours(17)), Duration.ofHours(8));
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(expired)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    void tokenSignedWithOtherKeyIs401() throws Exception {
        // Ktoś podrobił token z rolą DISPATCHER, nie znając naszego klucza
        var foreignKey = new SecretKeySpec("attacker-secret-attacker-secret-attacker!".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256");
        String forged = sign(new NimbusJwtEncoder(new ImmutableSecret<>(foreignKey)), Instant.now(), Duration.ofHours(8));
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    void tamperedRoleInPayloadIs401() throws Exception {
        // Kierowca zmienia w swoim tokenie "role":"DRIVER" na "DISPATCHER", zostawiając stary podpis
        String[] parts = loginToken("jan.kowalski").split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = payload.replace("\"DRIVER\"", "\"DISPATCHER\"");
        assertThat(forgedPayload).contains("DISPATCHER");
        String forged = parts[0] + "." + base64Url(forgedPayload) + "." + parts[2];

        // Podpis nie pasuje do zmienionej treści → token odrzucony
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(forged)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid or expired token"));
    }

    @Test
    void unsignedTokenWithAlgNoneIs401() throws Exception {
        // Klasyczny atak: nagłówek "alg":"none" i brak podpisu — dekoder akceptuje tylko HS256
        long now = Instant.now().getEpochSecond();
        String header = base64Url("{\"alg\":\"none\"}");
        String payload = base64Url("{\"iss\":\"truckdispatch\",\"sub\":\"dyspozytor\",\"role\":\"DISPATCHER\","
                + "\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}");
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(header + "." + payload + ".")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void driverLoginTokenOpensOnlyOwnPanel() throws Exception {
        // Pełna ścieżka na prawdziwym tokenie: logowanie → claim driverId → /api/me
        String token = loginToken("piotr.nowak");
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.id").value(2))
                .andExpect(jsonPath("$.driver.lastName").value("Nowak"));
        mockMvc.perform(get("/api/drivers/2").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void noSessionCookieIsCreated() throws Exception {
        // Bezstanowo: każde żądanie niesie token, serwer nie trzyma sesji (stąd brak CSRF)
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(loginToken())))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
