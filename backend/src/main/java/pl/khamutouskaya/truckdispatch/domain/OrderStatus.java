package pl.khamutouskaya.truckdispatch.domain;

public enum OrderStatus {
    NEW,          // создан, водитель не назначен
    ASSIGNED,     // назначен водителю
    IN_PROGRESS,  // выполняется
    DONE,         // выполнен
    CANCELLED     // отменён
}
