package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import java.util.ArrayList;
import java.util.List;

import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.BREAK_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.DAILY_REST_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_CONTINUOUS_DRIVING_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_DAILY_DRIVING_MIN;
import static pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules.MAX_WEEKLY_DRIVING_MIN;

/**
 * Zegar czasu pracy kierowcy — jedno miejsce, w którym zapisane są reguły 561/2006.
 * Używają go: {@link WorkTimeSimulator} (prognoza przy przydziale) i symulator ruchu (gdzie stoi kierowca).
 * <p>
 * Zasady:
 * <ul>
 *   <li>4,5 h jazdy bez przerwy → przerwa 45 min (zeruje licznik „bez przerwy”);</li>
 *   <li>9 h jazdy od ostatniego odpoczynku → odpoczynek 11 h (zeruje licznik dzienny i „bez przerwy”);
 *       jeśli oba limity wypadają w tej samej chwili — tylko odpoczynek, bo obejmuje przerwę;</li>
 *   <li>56 h jazdy w tygodniu → flaga weeklyLimitExceeded (decyzję o odrzuceniu podejmuje filtr);</li>
 *   <li>praca (załadunek, rozładunek) nie dodaje czasu jazdy, ale nie jest przerwą.</li>
 * </ul>
 * Postój wstawiamy dopiero wtedy, gdy trzeba jechać dalej: jeśli limit wypada równo z końcem jazdy, postoju nie ma.
 * Obiekt ze stanem — tworzyć nowy na każdą symulację.
 */
public final class DrivingClock {

    // Tolerancja porównań liczb zmiennoprzecinkowych [min]
    private static final double EPS = 1e-9;

    private double timeMin;
    private double todayMin;
    private double weekMin;
    private double continuousMin;
    private int breakCount;
    private int restCount;
    private boolean weeklyLimitExceeded;

    public DrivingClock(DriverWorkState state) {
        todayMin = state.drivingTodayMin();
        weekMin = state.drivingWeekMin();
        continuousMin = state.continuousDrivingMin();
    }

    /**
     * Jazda przez podany czas z automatycznymi postojami.
     *
     * @return postoje wstawione w trakcie tego odcinka (pusta lista, jeśli żadnego)
     */
    public List<Stop> drive(double drivingMin) {
        List<Stop> stops = new ArrayList<>();
        double driven = 0;
        while (drivingMin - driven > EPS) {
            if (MAX_DAILY_DRIVING_MIN - todayMin <= EPS) {
                stops.add(new Stop(StopType.REST, timeMin, driven));
                rest();
            } else if (MAX_CONTINUOUS_DRIVING_MIN - continuousMin <= EPS) {
                stops.add(new Stop(StopType.BREAK, timeMin, driven));
                takeBreak();
            }
            // Jedziemy do najbliższego z: końca odcinka, limitu „bez przerwy”, limitu dziennego
            double chunk = Math.min(drivingMin - driven,
                    Math.min(MAX_CONTINUOUS_DRIVING_MIN - continuousMin, MAX_DAILY_DRIVING_MIN - todayMin));
            timeMin += chunk;
            todayMin += chunk;
            weekMin += chunk;
            continuousMin += chunk;
            driven += chunk;
            if (weekMin - MAX_WEEKLY_DRIVING_MIN > EPS) {
                weeklyLimitExceeded = true;
            }
        }
        return stops;
    }

    // Praca bez jazdy: czas płynie, liczniki jazdy bez zmian
    public void work(double workMin) {
        timeMin += workMin;
    }

    private void takeBreak() {
        timeMin += BREAK_MIN;
        continuousMin = 0;
        breakCount++;
    }

    private void rest() {
        timeMin += DAILY_REST_MIN;
        todayMin = 0;
        continuousMin = 0;
        restCount++;
    }

    public double timeMin() {
        return timeMin;
    }

    public int breakCount() {
        return breakCount;
    }

    public int restCount() {
        return restCount;
    }

    public boolean weeklyLimitExceeded() {
        return weeklyLimitExceeded;
    }

    public DriverWorkState state() {
        return new DriverWorkState(todayMin, weekMin, continuousMin);
    }
}
