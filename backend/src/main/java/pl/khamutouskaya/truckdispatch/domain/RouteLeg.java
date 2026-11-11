package pl.khamutouskaya.truckdispatch.domain;

// Odcinek trasy zlecenia
public enum RouteLeg {
    EMPTY,   // dojazd pustym pojazdem do miejsca załadunku
    LOADED   // z ładunkiem: załadunek → rozładunek
}
