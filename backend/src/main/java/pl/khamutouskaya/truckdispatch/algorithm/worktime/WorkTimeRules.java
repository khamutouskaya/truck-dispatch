package pl.khamutouskaya.truckdispatch.algorithm.worktime;

/**
 * Normy czasu pracy kierowcy wg rozporządzenia (WE) nr 561/2006 — wersja uproszczona.
 * <ul>
 *   <li>art. 7: po 4,5 h jazdy — przerwa 45 min;</li>
 *   <li>art. 6 ust. 1: dzienny czas jazdy do 9 h, potem odpoczynek dobowy 11 h (art. 8);</li>
 *   <li>art. 6 ust. 2: tygodniowy czas jazdy do 56 h.</li>
 * </ul>
 * Uproszczenia (świadome): bez dzielenia przerwy na 15 + 30 min, bez wydłużenia dnia do 10 h
 * dwa razy w tygodniu, bez skróconych odpoczynków 9 h i bez limitu 90 h na dwa tygodnie.
 */
public final class WorkTimeRules {

    public static final double MAX_CONTINUOUS_DRIVING_MIN = 4.5 * 60; // 270
    public static final double BREAK_MIN = 45;
    public static final double MAX_DAILY_DRIVING_MIN = 9 * 60;        // 540
    public static final double DAILY_REST_MIN = 11 * 60;              // 660
    public static final double MAX_WEEKLY_DRIVING_MIN = 56 * 60;      // 3360

    private WorkTimeRules() {
    }
}
