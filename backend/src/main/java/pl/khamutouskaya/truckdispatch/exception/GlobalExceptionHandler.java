package pl.khamutouskaya.truckdispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

// Przechwytuje wyjątki ze wszystkich kontrolerów i zamienia je
// na jednolity format odpowiedzi ProblemDetail (RFC 9457, application/problem+json)
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Pojedynczy błąd walidacji: które pole i co jest z nim nie tak
    public record FieldViolation(String field, String message) {
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Business rule violation", ex.getMessage());
    }

    // Wywoływany, gdy ciało z @Valid @RequestBody nie przeszło walidacji adnotacji Jakarta Validation
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<FieldViolation> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "Request contains invalid fields");
        problem.setProperty("errors", errors);
        return problem;
    }

    // Nie udało się odczytać ciała: uszkodzony JSON, nieznana wartość enum, błędny format daty
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body is not valid JSON or contains values of the wrong type");
    }

    // Parametr w adresie ma błędny typ: /api/drivers/abc lub ?status=UNKNOWN
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue());
    }

    // Brak wymaganego parametru w adresie, np. GET /api/routes bez vehicleId
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Required parameter '" + ex.getParameterName() + "' is missing");
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
