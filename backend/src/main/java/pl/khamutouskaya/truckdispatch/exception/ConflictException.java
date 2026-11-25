package pl.khamutouskaya.truckdispatch.exception;

import lombok.Getter;

import java.util.Map;

/**
 * Żądanie jest poprawne co do formy, ale narusza regułę biznesową → 409 Conflict.
 * code i params trafiają do odpowiedzi — frontend buduje z nich komunikat po polsku; message zostaje dla logów i Swaggera.
 */
@Getter
public class ConflictException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> params;

    public ConflictException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ConflictException(ErrorCode code, String message, Map<String, Object> params) {
        super(message);
        this.code = code;
        this.params = params;
    }
}
