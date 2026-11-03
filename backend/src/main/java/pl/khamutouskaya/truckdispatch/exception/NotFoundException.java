package pl.khamutouskaya.truckdispatch.exception;

// Запрошенный объект не существует → 404 Not Found
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    // Удобный конструктор: new NotFoundException("Driver", 5) → "Driver with id 5 not found"
    public NotFoundException(String entityName, Long id) {
        super(entityName + " with id " + id + " not found");
    }
}
