package pl.khamutouskaya.truckdispatch.domain;

public enum LocationSource {
    MANUAL,      // диспетчер ввёл вручную
    PHONE,       // пришло с телефона водителя
    SIMULATION   // сгенерировано симуляцией
}
