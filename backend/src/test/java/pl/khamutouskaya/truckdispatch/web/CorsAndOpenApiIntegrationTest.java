package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CORS dla Reacta (http://localhost:5173) i przycisk „Authorize” w Swaggerze.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsAndOpenApiIntegrationTest {

    private static final String REACT = "http://localhost:5173";

    @Autowired private MockMvc mockMvc;

    @Test
    void preflightFromReactIsAllowedWithoutToken() throws Exception {
        // Przeglądarka przed POST z nagłówkiem Authorization pyta OPTIONS — bez tokenu
        mockMvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, REACT)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REACT))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
    }

    @Test
    void preflightFromOtherOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/orders")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void reactResponseCarriesCorsHeaderEvenOn401() throws Exception {
        // Bez nagłówka CORS przeglądarka ukryłaby przed Reactem nawet kod 401 (nie wiedziałby, że trzeba się zalogować)
        mockMvc.perform(get("/api/orders").header(HttpHeaders.ORIGIN, REACT))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, REACT));
    }

    @Test
    void openApiDeclaresBearerTokenExceptForLoginAndPing() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray())
                // Puste wymaganie = endpoint bez kłódki w Swaggerze
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/ping'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/orders'].get.security").doesNotExist());
    }
}
