package pl.khamutouskaya.truckdispatch.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Błędy bezpieczeństwa (401, 403) powstają w filtrach Spring Security, jeszcze przed kontrolerem,
 * więc @RestControllerAdvice ich nie widzi. Ta klasa przekazuje je do GlobalExceptionHandler —
 * dzięki temu wszystkie błędy API mają jeden format ProblemDetail.
 */
@Component
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final HandlerExceptionResolver resolver;

    public SecurityProblemHandler(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    // 401: brak tokenu albo token zły (podpis, termin ważności, format)
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) {
        // Standard (RFC 6750): odpowiedź 401 mówi klientowi, jakiego uwierzytelnienia oczekujemy
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        resolver.resolveException(request, response, null, authException);
    }

    // 403: token poprawny, ale rola nie ma dostępu
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        resolver.resolveException(request, response, null, accessDeniedException);
    }
}
