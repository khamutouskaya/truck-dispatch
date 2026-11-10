package pl.khamutouskaya.truckdispatch.domain;

public enum DriverStatus {
    AVAILABLE,   // wolny, może przyjąć zlecenie
    ON_ROUTE,    // w trasie ze zleceniem
    RESTING,     // obowiązkowy odpoczynek
    OFF_DUTY     // poza służbą
}