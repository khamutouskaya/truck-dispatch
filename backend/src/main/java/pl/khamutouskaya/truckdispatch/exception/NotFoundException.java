package pl.khamutouskaya.truckdispatch.exception;

// Żądany obiekt nie istnieje → 404 Not Found
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    // Wygodny konstruktor: new NotFoundException("Driver", 5) → "Driver with id 5 not found"
    public NotFoundException(String entityName, Long id) {
        super(entityName + " with id " + id + " not found");
    }
}
