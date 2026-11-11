package pl.khamutouskaya.truckdispatch.algorithm.worktime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Reguły 561/2006 testuje WorkTimeSimulatorTest; tu — informacja o miejscu postojów (dla symulatora ruchu)
class DrivingClockTest {

    @Test
    void reportsWhereInTheDrivingStopsHappened() {
        // 10 h jazdy wypoczętego: przerwa po 270 min, odpoczynek po kolejnych 270 (razem 540 = 9 h)
        DrivingClock clock = new DrivingClock(DriverWorkState.rested());

        List<Stop> stops = clock.drive(600);

        assertThat(stops).extracting(Stop::type).containsExactly(StopType.BREAK, StopType.REST);
        assertThat(stops.get(0).drivenBeforeMin()).isCloseTo(270, within(1e-9));
        assertThat(stops.get(0).startMin()).isCloseTo(270, within(1e-9));
        assertThat(stops.get(1).drivenBeforeMin()).isCloseTo(540, within(1e-9));
        assertThat(stops.get(1).startMin()).isCloseTo(540 + 45, within(1e-9));
        assertThat(clock.timeMin()).isCloseTo(600 + 45 + 660, within(1e-9));
    }

    @Test
    void offsetsAreRelativeToCurrentDrivingCall() {
        DrivingClock clock = new DrivingClock(DriverWorkState.rested());
        clock.drive(200);
        clock.work(60);

        List<Stop> stops = clock.drive(100); // limit po 70 min tego odcinka

        assertThat(stops).hasSize(1);
        assertThat(stops.getFirst().drivenBeforeMin()).isCloseTo(70, within(1e-9));
        assertThat(stops.getFirst().startMin()).isCloseTo(200 + 60 + 70, within(1e-9));
    }
}
