package pl.khamutouskaya.truckdispatch.algorithm.assignment;

/**
 * Powód odrzucenia kierowcy — zapisywany dla każdego odrzuconego, żeby dyspozytor widział, dlaczego.
 * Kolejność stałych = kolejność sprawdzania.
 */
public enum RejectionReason {

    // --- Brak danych (sprawdzane najpierw) ---
    NO_VEHICLE,     // kierowca nie ma przypisanego pojazdu
    NO_LOCATION,    // nieznane położenie kierowcy

    // --- Szybki odsiew, bez szukania tras ---
    TYPE,           // inny typ nadwozia niż wymagany
    CAPACITY,       // za mała ładowność [kg] albo objętość [m³]
    TOO_FAR,        // ponad 800 km w linii prostej do załadunku

    // --- Po wyszukaniu tras ---
    NO_ROUTE,       // brak trasy do załadunku lub do rozładunku (np. wiadukt, limit masy)

    // --- Po symulacji czasu pracy (filtr twardy) ---
    LATE_LOADING,   // przyjazd na załadunek po terminie
    LATE_DELIVERY,  // koniec rozładunku po terminie dostawy
    WEEKLY_LIMIT    // przekroczony tygodniowy limit jazdy 56 h
}
