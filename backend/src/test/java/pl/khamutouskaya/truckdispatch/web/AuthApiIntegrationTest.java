package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/auth/login na prawdziwej bazie: konta demonstracyjne z V9 (hasło haslo123 dla wszystkich).
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JwtDecoder jwtDecoder;

    private ResultActions login(String username, String password) throws Exception {
        String body = jsonMapper.writeValueAsString(new LoginBody(username, password));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private record LoginBody(String username, String password) {
    }

    // Każde konto z V9 loguje się hasłem haslo123 — przy okazji: skróty $2y$ z htpasswd pasują do BCryptPasswordEncoder
    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "dyspozytor,         DISPATCHER, null",
            "jan.kowalski,       DRIVER,     1",
            "piotr.nowak,        DRIVER,     2",
            "grzegorz.szymanski, DRIVER,     9"})
    void demoAccountGetsTokenWithRoleAndDriver(String username, String role, Long driverId) throws Exception {
        Instant before = Instant.now();
        String json = login(username, "haslo123")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode response = jsonMapper.readTree(json);

        assertThat(response.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(response.get("username").asString()).isEqualTo(username);
        assertThat(response.get("role").asString()).isEqualTo(role);
        if (driverId == null) {
            assertThat(response.get("driverId").isNull()).isTrue();
        } else {
            assertThat(response.get("driverId").asLong()).isEqualTo(driverId);
        }

        // Token przechodzi weryfikację podpisu i ma właściwą zawartość
        Jwt jwt = jwtDecoder.decode(response.get("accessToken").asString());
        assertThat(jwt.getSubject()).isEqualTo(username);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("truckdispatch");
        assertThat(jwt.getClaimAsString("role")).isEqualTo(role);
        if (driverId == null) {
            assertThat(jwt.hasClaim("driverId")).isFalse();
        } else {
            assertThat(((Number) jwt.getClaim("driverId")).longValue()).isEqualTo(driverId);
        }
        // Ważny 8 h od wydania; expiresAt w odpowiedzi = exp w tokenie
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(8));
        assertThat(jwt.getIssuedAt()).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(Instant.parse(response.get("expiresAt").asString())).isEqualTo(jwt.getExpiresAt());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSame401() throws Exception {
        login("dyspozytor", "zle-haslo")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        // Ta sama odpowiedź dla nieistniejącego konta — nie da się sprawdzić, czy login istnieje
        login("nie.istnieje", "haslo123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void blankCredentialsGive400() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }
}
