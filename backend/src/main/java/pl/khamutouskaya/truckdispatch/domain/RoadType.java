package pl.khamutouskaya.truckdispatch.domain;

// Klasa drogi; od niej zależy prędkość bazowa v_base (km/h)
public enum RoadType {
    MOTORWAY,    // autostrada (A1, A2, A4…), 80 km/h
    EXPRESSWAY,  // droga ekspresowa (S3, S7, S8…), 75 km/h
    NATIONAL,    // droga krajowa, 65 km/h
    REGIONAL,    // droga wojewódzka, 55 km/h
    LOCAL        // droga lokalna, 45 km/h
}
