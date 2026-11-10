package pl.khamutouskaya.truckdispatch.exception;

// Żądanie jest poprawne co do formy, ale narusza regułę biznesową → 409 Conflict
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
