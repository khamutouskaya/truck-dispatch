package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import java.time.LocalDateTime;

import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.BREAK_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.DAILY_REST_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_CONTINUOUS_DRIVING_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_DAILY_DRIVING_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_WEEKLY_DRIVING_MIN;

/**
 * Odtwarza zlecenie minuta po minucie: jazda pusta → załadunek → jazda z ładunkiem → rozładunek,
 * wstawiając przerwy i odpoczynki dokładnie w chwili osiągnięcia limitu (także w środku odcinka drogi).
 * <p>
 * Zasady:
 * <ul>
 *   <li>4,5 h jazdy bez przerwy → przerwa 45 min (zeruje licznik „bez przerwy”);</li>
 *   <li>9 h jazdy od ostatniego odpoczynku → odpoczynek 11 h (zeruje licznik dzienny i „bez przerwy”);
 *       jeśli oba limity wypadają w tej samej chwili — tylko odpoczynek, bo obejmuje przerwę;</li>
 *   <li>56 h jazdy w tygodniu → dalej jechać nie wolno: ustawiamy flagę weeklyLimitExceeded
 *       (symulacja liczy dalej, ale kandydat i tak zostanie odrzucony z powodem WEEKLY_LIMIT);</li>
 *   <li>załadunek i rozładunek to praca: nie dodają czasu jazdy, ale nie są też przerwą.</li>
 * </ul>
 * Przerwę robimy dopiero wtedy, gdy trzeba jechać dalej: jeśli limit wypada równo z końcem jazdy,
 * przerwa nie jest liczona w tym zleceniu.
 */
public final class WorkTimeSimulator {

    // Tolerancja porównań liczb zmiennoprzecinkowych [min]
    private static final double EPS = 1e-9;

    private WorkTimeSimulator() {
    }

    public static WorkTimeSimulation simulate(LocalDateTime start, DriverWorkState state, TripPlan trip) {
        Clock clock = new Clock(state);

        clock.drive(trip.emptyDrivingMin());
        double arrivalAtLoadingMin = clock.timeMin;
        clock.work(trip.loadingMin());
        clock.drive(trip.loadedDrivingMin());
        clock.work(trip.unloadingMin());

        return new WorkTimeSimulation(start, arrivalAtLoadingMin, clock.timeMin, clock.breakCount, clock.restCount,
                new DriverWorkState(clock.todayMin, clock.weekMin, clock.continuousMin), clock.weeklyLimitExceeded);
    }

    // Stan symulacji: zegar i liczniki; obiekt pomocniczy żyje tylko w jednym wywołaniu simulate()
    private static final class Clock {
        double timeMin;
        double todayMin;
        double weekMin;
        double continuousMin;
        int breakCount;
        int restCount;
        boolean weeklyLimitExceeded;

        Clock(DriverWorkState state) {
            todayMin = state.drivingTodayMin();
            weekMin = state.drivingWeekMin();
            continuousMin = state.continuousDrivingMin();
        }

        void drive(double drivingMin) {
            double remaining = drivingMin;
            while (remaining > EPS) {
                if (MAX_DAILY_DRIVING_MIN - todayMin <= EPS) {
                    rest();
                } else if (MAX_CONTINUOUS_DRIVING_MIN - continuousMin <= EPS) {
                    takeBreak();
                }
                // Jedziemy do najbliższego z: końca odcinka, limitu „bez przerwy”, limitu dziennego
                double chunk = Math.min(remaining,
                        Math.min(MAX_CONTINUOUS_DRIVING_MIN - continuousMin, MAX_DAILY_DRIVING_MIN - todayMin));
                timeMin += chunk;
                todayMin += chunk;
                weekMin += chunk;
                continuousMin += chunk;
                remaining -= chunk;
                if (weekMin - MAX_WEEKLY_DRIVING_MIN > EPS) {
                    weeklyLimitExceeded = true;
                }
            }
        }

        void work(double workMin) {
            timeMin += workMin; // czas płynie, liczniki jazdy bez zmian
        }

        void takeBreak() {
            timeMin += BREAK_MIN;
            continuousMin = 0;
            breakCount++;
        }

        void rest() {
            timeMin += DAILY_REST_MIN;
            todayMin = 0;
            continuousMin = 0;
            restCount++;
        }
    }
}
