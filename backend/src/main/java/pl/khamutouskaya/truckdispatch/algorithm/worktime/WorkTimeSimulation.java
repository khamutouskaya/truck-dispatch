package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import java.time.LocalDateTime;

/**
 * Wynik symulacji zlecenia z uwzględnieniem przerw i odpoczynków.
 *
 * @param start               chwila rozpoczęcia (zwykle „teraz”)
 * @param arrivalAtLoadingMin przyjazd na załadunek [min od startu] (K2)
 * @param finishMin           koniec rozładunku [min od startu]
 * @param breakCount          liczba przerw 45 min
 * @param restCount           liczba odpoczynków dobowych 11 h (K4 = breakCount + restCount)
 * @param endState            liczniki jazdy po zakończeniu zlecenia (K3 = endState.remainingDrivingTodayMin())
 * @param weeklyLimitExceeded czy w trakcie zlecenia przekroczono 56 h jazdy w tygodniu (odrzucenie WEEKLY_LIMIT)
 */
public record WorkTimeSimulation(
        LocalDateTime start,
        double arrivalAtLoadingMin,
        double finishMin,
        int breakCount,
        int restCount,
        DriverWorkState endState,
        boolean weeklyLimitExceeded
) {

    public LocalDateTime arrivalAtLoading() {
        return plusMinutes(start, arrivalAtLoadingMin);
    }

    public LocalDateTime finish() {
        return plusMinutes(start, finishMin);
    }

    // Liczba przerw i odpoczynków razem (kryterium K4)
    public int stopCount() {
        return breakCount + restCount;
    }

    // Czasy liczymy w minutach typu double; do daty dodajemy z dokładnością do sekundy
    private static LocalDateTime plusMinutes(LocalDateTime time, double minutes) {
        return time.plusSeconds(Math.round(minutes * 60));
    }
}
