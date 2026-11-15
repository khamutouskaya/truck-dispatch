package pl.khamutouskaya.truckdispatch.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI z przyciskiem „Authorize”: wklejony token JWT jest dołączany do każdego żądania
 * jako Authorization: Bearer …. Wymaganie tokenu jest globalne; wyjątki (logowanie, ping) mają adnotację
 * {@code @SecurityRequirements()} w kontrolerze.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Truck dispatch API").version("1.0")
                        .description("Log in with POST /api/auth/login, then click Authorize and paste the accessToken."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
