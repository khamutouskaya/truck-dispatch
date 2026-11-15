package pl.khamutouskaya.truckdispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Adresy frontendu, z których przeglądarka może wywoływać API (application.yaml, cors.*).
 *
 * @param allowedOrigins np. http://localhost:5173 (serwer deweloperski Vite)
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
