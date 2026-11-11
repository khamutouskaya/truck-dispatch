package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Przykład z docs/karta-i-algorytm.md, część 4: zlecenie Łódź → Kraków, start 08:00,
 * załadunek i rozładunek po 60 min, jazda z ładunkiem 3 h 44 min = 224 min.
 * Każdy kierowca musi dać dokładnie te liczby, które są w dokumencie.
 */
class WorkTimeSimulatorTest {

    private static final double EPS = 1e-9;
    private static final LocalDateTime EIGHT = LocalDateTime.of(2026, 11, 11, 8, 0);
    private static final double LOADED_MIN = 224;

    private static TripPlan trip(double emptyDrivingMin) {
        return new TripPlan(emptyDrivingMin, 60, LOADED_MIN, 60);
    }

    private static LocalDateTime at(int hour, int minute) {
        return EIGHT.withHour(hour).withMinute(minute);
    }

    // --- Przykład z dokumentu ---

    @Test
    void driverA_breakInTheMiddleOfLoadedLeg() {
        // Warszawa → Łódź 1 h 41 min; dziś 2 h, bez przerwy 2 h, tydzień 30 h
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT,
                new DriverWorkState(120, 30 * 60, 120), trip(101));

        // 09:41 na załadunku (bez przerwy 3:41) → 10:41 → 49 min jazdy do 4,5 h → 11:30 przerwa → 12:15 → 15:10 → 16:10
        assertThat(sim.arrivalAtLoading()).isEqualTo(at(9, 41));
        assertThat(sim.finish()).isEqualTo(at(16, 10));
        assertThat(sim.breakCount()).isEqualTo(1);
        assertThat(sim.restCount()).isZero();
        // Dziś: 2 h + 1:41 + 3:44 = 7 h 25 min → zapas 1 h 35 min
        assertThat(sim.endState().drivingTodayMin()).isCloseTo(445, within(EPS));
        assertThat(sim.endState().remainingDrivingTodayMin()).isCloseTo(95, within(EPS));
        assertThat(sim.weeklyLimitExceeded()).isFalse();
    }

    @Test
    void driverB_nearestDriverMustTakeDailyRestAndIsLate() {
        // Piotrków → Łódź 38 min; dziś już 8 h 40 min, bez przerwy 1 h, tydzień 40 h
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT,
                new DriverWorkState(520, 40 * 60, 60), trip(38));

        // 20 min do limitu 9 h → odpoczynek 11 h → jeszcze 18 min → na załadunku 19:38 (termin 12:00 → LATE_LOADING)
        assertThat(sim.arrivalAtLoading()).isEqualTo(at(19, 38));
        assertThat(sim.restCount()).isEqualTo(1);
        assertThat(sim.breakCount()).isZero();
        // Po odpoczynku: 18 + 224 = 242 min jazdy, bez przerwy też 242 < 270 → przerwa niepotrzebna
        assertThat(sim.endState().drivingTodayMin()).isCloseTo(242, within(EPS));
        // 19:38 + 60 + 224 + 60 = 01:22 następnego dnia
        assertThat(sim.finish()).isEqualTo(EIGHT.plusDays(1).withHour(1).withMinute(22));
    }

    @Test
    void driverD_startsLaterAndEndsWithSevenMinutesLeft() {
        // Radom → Łódź 2 h 09 min, start 09:30; dziś 3 h, bez przerwy 1 h, tydzień 20 h
        LocalDateTime start = at(9, 30);
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(start,
                new DriverWorkState(180, 20 * 60, 60), trip(129));

        // 11:39 na załadunku → 12:39 → 1 h 21 min → 14:00 przerwa → 14:45 → 2 h 23 min → 17:08 → 18:08
        assertThat(sim.arrivalAtLoading()).isEqualTo(at(11, 39));
        assertThat(sim.finish()).isEqualTo(at(18, 8));
        assertThat(sim.breakCount()).isEqualTo(1);
        assertThat(sim.endState().remainingDrivingTodayMin()).isCloseTo(7, within(EPS));
    }

    @Test
    void driverE_restedDriverFromKatowice() {
        // Katowice → Łódź 2 h 30 min; dziś 0, tydzień 10 h
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT,
                new DriverWorkState(0, 10 * 60, 0), trip(150));

        // 10:30 na załadunku → 11:30 → 2 h → 13:30 przerwa → 14:15 → 1 h 44 min → 15:59 → 16:59
        assertThat(sim.arrivalAtLoading()).isEqualTo(at(10, 30));
        assertThat(sim.finish()).isEqualTo(at(16, 59));
        assertThat(sim.stopCount()).isEqualTo(1);
        // Dziś 6 h 14 min → zapas 2 h 46 min; tydzień 10 h + 6 h 14 min
        assertThat(sim.endState().remainingDrivingTodayMin()).isCloseTo(166, within(EPS));
        assertThat(sim.endState().drivingWeekMin()).isCloseTo(974, within(EPS));
    }

    // --- Przypadki brzegowe ---

    @Test
    void loadingIsWorkNotBreak() {
        // 4 h jazdy, 2 h załadunku, 1 h jazdy: bez przerwy wciąż 4 h → po 30 min przerwa
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, DriverWorkState.rested(),
                new TripPlan(240, 120, 60, 0));

        assertThat(sim.breakCount()).isEqualTo(1);
        assertThat(sim.finishMin()).isCloseTo(240 + 120 + 60 + 45, within(EPS));
        assertThat(sim.endState().continuousDrivingMin()).isCloseTo(30, within(EPS));
    }

    @Test
    void whenBothLimitsHitAtOnceOnlyDailyRestIsTaken() {
        // Dziś 8 h 20 min, bez przerwy 3 h 50 min: po 40 min jednocześnie 9 h i 4,5 h → tylko odpoczynek
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, new DriverWorkState(500, 500, 230),
                new TripPlan(60, 0, 0, 0));

        assertThat(sim.restCount()).isEqualTo(1);
        assertThat(sim.breakCount()).isZero();
        assertThat(sim.finishMin()).isCloseTo(40 + 660 + 20, within(EPS));
        assertThat(sim.endState().drivingTodayMin()).isCloseTo(20, within(EPS));
        assertThat(sim.endState().continuousDrivingMin()).isCloseTo(20, within(EPS));
    }

    @Test
    void longTripHasBreakThenDailyRest() {
        // 10 h jazdy wypoczętego: 4,5 h → przerwa → 4,5 h (razem 9 h) → odpoczynek 11 h → 1 h
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, DriverWorkState.rested(),
                new TripPlan(600, 0, 0, 0));

        assertThat(sim.breakCount()).isEqualTo(1);
        assertThat(sim.restCount()).isEqualTo(1);
        assertThat(sim.finishMin()).isCloseTo(600 + 45 + 660, within(EPS));
        assertThat(sim.endState().drivingTodayMin()).isCloseTo(60, within(EPS));
        assertThat(sim.endState().drivingWeekMin()).isCloseTo(600, within(EPS));
    }

    @Test
    void noBreakWhenLimitIsReachedExactlyAtTheEndOfDriving() {
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, DriverWorkState.rested(),
                new TripPlan(270, 60, 0, 0));

        assertThat(sim.breakCount()).isZero();
        assertThat(sim.finishMin()).isCloseTo(330, within(EPS));
    }

    @Test
    void driverWhoAlreadyReachedContinuousLimitStartsWithBreak() {
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, new DriverWorkState(270, 270, 270),
                new TripPlan(10, 0, 0, 0));

        assertThat(sim.breakCount()).isEqualTo(1);
        assertThat(sim.arrivalAtLoadingMin()).isCloseTo(55, within(EPS));
    }

    @Test
    void exceedingWeeklyLimitIsFlagged() {
        // Tydzień 55 h 50 min + 30 min jazdy = 56 h 20 min > 56 h
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, new DriverWorkState(0, 3350, 0),
                new TripPlan(30, 0, 0, 0));

        assertThat(sim.weeklyLimitExceeded()).isTrue();
        assertThat(sim.endState().remainingDrivingWeekMin()).isZero();
    }

    @Test
    void reachingWeeklyLimitExactlyIsAllowed() {
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT, new DriverWorkState(0, 3330, 0),
                new TripPlan(30, 0, 0, 0));

        assertThat(sim.weeklyLimitExceeded()).isFalse();
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> new DriverWorkState(-1, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TripPlan(10, -5, 10, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
