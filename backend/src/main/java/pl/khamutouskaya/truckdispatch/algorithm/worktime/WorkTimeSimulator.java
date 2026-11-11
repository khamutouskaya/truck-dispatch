package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import java.time.LocalDateTime;

/**
 * Prognoza zlecenia w czasie: jazda pusta → załadunek → jazda z ładunkiem → rozładunek,
 * z przerwami i odpoczynkami wstawianymi dokładnie w chwili osiągnięcia limitu (także w środku odcinka drogi).
 * Same reguły 561/2006 są w {@link DrivingClock}.
 */
public final class WorkTimeSimulator {

    private WorkTimeSimulator() {
    }

    public static WorkTimeSimulation simulate(LocalDateTime start, DriverWorkState state, TripPlan trip) {
        DrivingClock clock = new DrivingClock(state);

        clock.drive(trip.emptyDrivingMin());
        double arrivalAtLoadingMin = clock.timeMin();
        clock.work(trip.loadingMin());
        clock.drive(trip.loadedDrivingMin());
        clock.work(trip.unloadingMin());

        return new WorkTimeSimulation(start, arrivalAtLoadingMin, clock.timeMin(), clock.breakCount(),
                clock.restCount(), clock.state(), clock.weeklyLimitExceeded());
    }
}
