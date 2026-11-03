package pl.khamutouskaya.truckdispatch.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

// Перехватывает исключения из всех контроллеров и превращает их
// в единый формат ответа ProblemDetail (RFC 9457, application/problem+json)
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Одна ошибка валидации: какое поле и что с ним не так
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

    // Срабатывает, когда тело с @Valid @RequestBody не прошло аннотации Jakarta Validation
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

    // Тело не удалось прочитать: битый JSON, неизвестное значение enum, неверный формат даты
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Request body is not valid JSON or contains values of the wrong type");
    }

    // Параметр в адресе неверного типа: /api/drivers/abc или ?status=UNKNOWN
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request",
                "Parameter '" + ex.getName() + "' has invalid value: " + ex.getValue());
    }

    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
