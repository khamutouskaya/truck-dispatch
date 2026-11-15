package pl.khamutouskaya.truckdispatch.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import pl.khamutouskaya.truckdispatch.domain.Role;
import pl.khamutouskaya.truckdispatch.service.TokenService;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Spring Security: hasła (BCrypt) i tokeny JWT podpisane HMAC-SHA256 jednym kluczem tajnym —
 * ta sama aplikacja wydaje tokeny (logowanie) i je sprawdza (każde żądanie), więc wystarcza klucz symetryczny.
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    // Skróty haseł w app_user (V9) są w formacie BCrypt
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecretKey jwtSecretKey(JwtProperties properties) {
        return new SecretKeySpec(properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    // Wydawanie tokenów przy logowaniu
    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    // Sprawdzanie tokenów w nagłówku Authorization: Bearer … — podpis i termin ważności (exp)
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * CORS: React działa pod innym adresem (Vite, port 5173) niż API (8080), więc przeglądarka najpierw pyta
     * serwer (żądanie OPTIONS, tzw. preflight), czy wolno. Odpowiadamy „tak” tylko dla adresów z konfiguracji.
     * Ciasteczka nie są potrzebne — token idzie w nagłówku Authorization.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setAllowCredentials(false);
        cors.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /**
     * Rola z tokenu → uprawnienie Springa: claim "role": "DRIVER" → ROLE_DRIVER.
     * Na tej podstawie działają reguły hasRole(...) poniżej.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(TokenService.ROLE_CLAIM);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Reguły dostępu: wygrywa pierwsza pasująca, wszystko niewymienione jest zabronione.
     * /api/me/** tylko DRIVER — dyspozytor nie ma własnego kierowcy. Brak tokenu → 401, zła rola → 403.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemHandler problemHandler,
                                                   JwtAuthenticationConverter jwtAuthenticationConverter)
            throws Exception {
        return http
                // REST z tokenem w nagłówku, bez ciasteczek sesji — CSRF nie dotyczy
                .csrf(csrf -> csrf.disable())
                // CORS przed uwierzytelnieniem: preflight OPTIONS nie niesie tokenu
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ping").permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/me/**").hasRole(Role.DRIVER.name())
                        .requestMatchers(HttpMethod.GET, "/api/nodes/**", "/api/edges/**", "/api/routes/**")
                                .hasAnyRole(Role.DISPATCHER.name(), Role.DRIVER.name())
                        .requestMatchers("/api/**").hasRole(Role.DISPATCHER.name())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .build();
    }
}
