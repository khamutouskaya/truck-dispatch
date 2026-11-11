package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

/**
 * Jedno kryterium oceny kandydata. Nowe kryterium = jedna nowa klasa, reszta algorytmu bez zmian.
 */
public interface Criterion {

    // Nazwa z jednostką, np. "distanceToLoadingKm" — klucz w wyjaśnieniu decyzji (JSON)
    String name();

    // Surowa wartość kryterium dla kandydata, przed normalizacją
    double rawValue(Candidate candidate, TransportOrder order);

    // true: większa wartość jest lepsza; false: mniejsza jest lepsza
    boolean higherIsBetter();
}
