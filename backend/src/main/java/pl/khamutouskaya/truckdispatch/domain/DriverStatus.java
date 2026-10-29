package pl.khamutouskaya.truckdispatch.domain;

public enum DriverStatus {
    AVAILABLE,   // свободен, может взять заказ
    ON_ROUTE,    // едет с заказом
    RESTING,     // обязательный отдых
    OFF_DUTY     // не на смене
}