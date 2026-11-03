package pl.khamutouskaya.truckdispatch.exception;

// Запрос корректен по форме, но нарушает бизнес-правило → 409 Conflict
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
