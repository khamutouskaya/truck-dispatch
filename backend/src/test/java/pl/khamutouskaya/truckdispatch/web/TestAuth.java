package pl.khamutouskaya.truckdispatch.web;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Uwierzytelnienie w testach MockMvc bez logowania: jwt() podstawia gotowy, „zweryfikowany” token
 * z podanymi claims i rolą. Samo wydawanie i sprawdzanie prawdziwych tokenów testują
 * AuthApiIntegrationTest i SecurityApiIntegrationTest.
 */
final class TestAuth {

    private TestAuth() {
    }

    static RequestPostProcessor dispatcher() {
        return jwt().jwt(token -> token.subject("dyspozytor").claim("role", "DISPATCHER"))
                .authorities(new SimpleGrantedAuthority("ROLE_DISPATCHER"));
    }

    static RequestPostProcessor driver(long driverId, String username) {
        return jwt().jwt(token -> token.subject(username).claim("role", "DRIVER").claim("driverId", driverId))
                .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    // MockMvc, w którym każde żądanie domyślnie idzie z podanym tokenem
    static MockMvc mockMvcAs(WebApplicationContext context, RequestPostProcessor user) {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user))
                .build();
    }
}
