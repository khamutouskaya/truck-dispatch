package pl.khamutouskaya.truckdispatch.domain;

public enum OrderStatus {
    NEW,          // utworzone, kierowca nieprzypisany
    ASSIGNED,     // przypisane kierowcy
    IN_PROGRESS,  // w realizacji
    DONE,         // zrealizowane
    CANCELLED     // anulowane
}
