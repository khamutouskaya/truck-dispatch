package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reguły dostępu według ról — na prawdziwych tokenach z logowania (sprawdza też zamianę claim "role" → ROLE_…).
 * @Transactional: żądania, które przejdą (dyspozytor), nie zostawią śladu w bazie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoleAccessIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private JwtEncoder jwtEncoder;

    private String dispatcherToken;
    private String driverToken;

    @BeforeEach
    void logIn() throws Exception {
        dispatcherToken = login("dyspozytor");
        driverToken = login("jan.kowalski");
    }

    private String login(String username) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"%s\", \"password\": \"haslo123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("accessToken").asString();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    // Kierowca: odczyt mapy dozwolony
    @ParameterizedTest
    @CsvSource({
            "/api/nodes",
            "/api/nodes/1",
            "/api/routes?fromNodeId=1&toNodeId=2&vehicleId=1"})
    void driverCanReadMap(String url) throws Exception {
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, bearer(driverToken)))
                .andExpect(status().isOk());
    }

    // Kierowca: wszystko, co należy do dyspozytora — 403 (także dane o sobie przez /api/drivers/1)
    @ParameterizedTest
    @CsvSource({
            "GET,    /api/drivers",
            "GET,    /api/drivers/1",
            "PATCH,  /api/drivers/1/location",
            "PATCH,  /api/drivers/1/status",
            "GET,    /api/vehicles",
            "GET,    /api/orders",
            "POST,   /api/orders",
            "PATCH,  /api/orders/1/cancel",
            "POST,   /api/orders/1/assign",
            "GET,    /api/orders/1/candidates",
            "GET,    /api/orders/1/assignment"})
    void driverIsForbiddenFromDispatcherApi(String method, String url) throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), url)
                        .header(HttpHeaders.AUTHORIZATION, bearer(driverToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("Your role has no access to this resource"));
    }

    @Test
    void dispatcherHasFullApiButNoDriverPanel() throws Exception {
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(dispatcherToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/nodes").header(HttpHeaders.AUTHORIZATION, bearer(dispatcherToken)))
                .andExpect(status().isOk());
        // /api/me/** — „moje dane kierowcy”; dyspozytor nie jest kierowcą
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(dispatcherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void validTokenWithoutRoleIsForbiddenEverywhere() throws Exception {
        // Podpis poprawny, ale brak claim "role" → brak uprawnień → domyślnie zabronione
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("truckdispatch").subject("ktos")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plus(Duration.ofHours(1))).build();
        String noRole = jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        mockMvc.perform(get("/api/nodes").header(HttpHeaders.AUTHORIZATION, bearer(noRole)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/drivers").header(HttpHeaders.AUTHORIZATION, bearer(noRole)))
                .andExpect(status().isForbidden());
    }
}
