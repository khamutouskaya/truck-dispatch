package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.RejectionReason;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Decyzja algorytmu przydziału z pełnym wyjaśnieniem. Ten sam obiekt:
 * <ul>
 *   <li>zapisywany jako JSON w assignment_result.details;</li>
 *   <li>zwracany przez API do ekranu „Szczegóły zlecenia” w React.</li>
 * </ul>
 * Budowa pod tabelę rankingu w React:
 * <pre>
 * kolumny:  criteria.map(c =&gt; c.name)                          — kolejność jak w criteria
 * wiersze:  ranking (już posortowany, rank 1 = zwycięzca)
 * komórka:  candidate.values[c.name].normalized / .raw / .weighted
 * pod tabelą: rejected — kto i dlaczego odpadł
 * </pre>
 *
 * @param orderId          zlecenie
 * @param evaluatedAt      chwila obliczenia („teraz” algorytmu)
 * @param assignedDriverId zwycięzca albo null, gdy nikt nie przeszedł filtrów
 * @param criteria         kryteria w kolejności kolumn, z wagami użytymi w tej decyzji
 * @param ranking          kandydaci po ocenie, od najlepszego
 * @param rejected         odrzuceni z powodem, w kolejności id kierowcy
 */
public record AssignmentDecision(
        Long orderId,
        LocalDateTime evaluatedAt,
        Long assignedDriverId,
        List<CriterionInfo> criteria,
        List<RankedCandidate> ranking,
        List<RejectedDriver> rejected
) {

    /**
     * Opis kolumny tabeli.
     *
     * @param name           klucz w RankedCandidate.values, np. "distanceToLoadingKm"
     * @param weight         waga 0..1
     * @param higherIsBetter kierunek: true — więcej znaczy lepiej
     */
    public record CriterionInfo(String name, double weight, boolean higherIsBetter) {
    }

    /**
     * Wartość jednego kryterium u kandydata.
     *
     * @param raw        surowa wartość w jednostkach z nazwy (km, h, szt., 0..1)
     * @param normalized po normalizacji min-max, 0..1 (1 = najlepszy wśród kandydatów)
     * @param weighted   wkład w score = weight × normalized; suma po kryteriach = score
     */
    public record CriterionValue(double raw, double normalized, double weighted) {
    }

    /**
     * Wiersz rankingu.
     *
     * @param rank                kolejne miejsce od 1
     * @param assigned            czy temu kierowcy przydzielono zlecenie (true tylko dla rank = 1)
     * @param values              nazwa kryterium → wartości (klucze jak w criteria[].name)
     * @param trip                przebieg zlecenia dla tego kierowcy (trasy i czas pracy)
     */
    public record RankedCandidate(
            int rank,
            long driverId,
            String driverName,
            String vehicleRegistration,
            double score,
            boolean assigned,
            Map<String, CriterionValue> values,
            TripSummary trip
    ) {
    }

    /**
     * Przebieg zlecenia: jazda pusta → załadunek → jazda z ładunkiem → rozładunek.
     *
     * @param distanceToLoadingKm      dojazd do załadunku [km] = pierwsza mila + trasa po grafie (K1)
     * @param firstMileKm              pierwsza mila: pozycja kierowcy → węzeł startowy [km]
     * @param loadedDistanceKm         trasa załadunek → rozładunek [km]
     * @param emptyDrivingMin          czas jazdy pustym, z pierwszą milą [min], bez przerw
     * @param loadedDrivingMin         czas jazdy z ładunkiem [min], bez przerw
     * @param arrivalAtLoading         przyjazd na załadunek (z przerwami i odpoczynkami)
     * @param finish                   koniec rozładunku
     * @param breakCount               przerwy 45 min
     * @param restCount                odpoczynki dobowe 11 h
     * @param remainingDrivingTodayMin zapas jazdy na dziś po zleceniu [min]
     */
    public record TripSummary(
            double distanceToLoadingKm,
            double firstMileKm,
            double loadedDistanceKm,
            double emptyDrivingMin,
            double loadedDrivingMin,
            LocalDateTime arrivalAtLoading,
            LocalDateTime finish,
            int breakCount,
            int restCount,
            double remainingDrivingTodayMin
    ) {
    }

    /**
     * Odrzucony kierowca.
     *
     * @param vehicleRegistration null, gdy kierowca nie ma pojazdu (NO_VEHICLE)
     * @param reason              powód odrzucenia — w React tłumaczony na tekst dla dyspozytora
     */
    public record RejectedDriver(long driverId, String driverName, String vehicleRegistration, RejectionReason reason) {
    }
}
