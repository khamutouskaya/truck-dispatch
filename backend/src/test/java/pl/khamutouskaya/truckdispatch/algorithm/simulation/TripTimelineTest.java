package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A(52°N, 20°E) —krawędź 0 (60 min)→ B(21°E) —krawędź 1 (240 min)→ C(22°E).
 * Kierowca wypoczęty, stoi w A. Załadunek w B 30 min, rozładunek w C 30 min.
 * <pre>
 *   0– 60  jazda A→B
 *  60– 90  załadunek (praca, nie przerwa: „bez przerwy” wciąż 60 min)
 *  90–300  jazda B→C, 210 min → razem 270 min bez przerwy
 * 300–345  przerwa 45 min w 210/240 = 0,875 długości krawędzi 1
 * 345–375  jazda reszty krawędzi 1 (30 min)
 * 375–405  rozładunek
 * </pre>
 */
class TripTimelineTest {

    private static final double EPS = 1e-9;
    private static final GeoPoint A = new GeoPoint(52.0, 20.0);
    private static final GeoPoint B = new GeoPoint(52.0, 21.0);
    private static final GeoPoint C = new GeoPoint(52.0, 22.0);
    private static final long EDGE_AB = 101, EDGE_BC = 102;

    private static TripTimeline build(GeoPoint driver, DriverWorkState state, TrafficModel traffic) {
        return TripTimeline.build(driver,
                List.of(new PlannedEdge(0, EDGE_AB, 60, List.of(A, B))), B, 30,
                List.of(new PlannedEdge(1, EDGE_BC, 240, List.of(B, C))), C, 30,
                state, traffic);
    }

    private static TripTimeline idealTrip() {
        return build(A, DriverWorkState.rested(), TrafficModel.ideal());
    }

    @Test
    void phasesFollowPlanWithBreakInTheMiddleOfLoadedEdge() {
        TripTimeline trip = idealTrip();

        assertThat(trip.loadingStartMin()).isCloseTo(60, within(EPS));
        assertThat(trip.finishMin()).isCloseTo(405, within(EPS));
        assertThat(trip.breakCount()).isEqualTo(1);
        assertThat(trip.restCount()).isZero();

        assertThat(trip.stateAt(10).activity()).isEqualTo(Activity.DRIVING);
        assertThat(trip.stateAt(70).activity()).isEqualTo(Activity.LOADING);
        assertThat(trip.stateAt(200).activity()).isEqualTo(Activity.DRIVING);
        assertThat(trip.stateAt(320).activity()).isEqualTo(Activity.BREAK);
        assertThat(trip.stateAt(360).activity()).isEqualTo(Activity.DRIVING);
        assertThat(trip.stateAt(390).activity()).isEqualTo(Activity.UNLOADING);
        assertThat(trip.stateAt(500).activity()).isEqualTo(Activity.FINISHED);
    }

    @Test
    void positionIsInterpolatedAlongEdgeGeometry() {
        TripTimeline trip = idealTrip();

        // W połowie pierwszej krawędzi
        TripState half = trip.stateAt(30);
        assertThat(half.position().lon()).isCloseTo(20.5, within(1e-6));
        assertThat(half.edgeSeq()).isZero();

        // Przerwa stoi w 0,875 krawędzi 1, a nie w węźle
        TripState onBreak = trip.stateAt(320);
        assertThat(onBreak.position().lon()).isCloseTo(21.875, within(1e-6));
        assertThat(onBreak.edgeSeq()).isEqualTo(1);

        // Po przerwie jedzie dalej od tego samego miejsca: w połowie reszty → 0,9375
        assertThat(trip.stateAt(360).position().lon()).isCloseTo(21.9375, within(1e-6));

        // Postoje w węzłach
        assertThat(trip.stateAt(70).position()).isEqualTo(B);
        assertThat(trip.stateAt(500).position()).isEqualTo(C);
    }

    @Test
    void endStateCountsOnlyDriving() {
        DriverWorkState end = idealTrip().endState();

        assertThat(end.drivingTodayMin()).isCloseTo(300, within(EPS));
        assertThat(end.drivingWeekMin()).isCloseTo(300, within(EPS));
        assertThat(end.continuousDrivingMin()).isCloseTo(30, within(EPS)); // od przerwy
    }

    @Test
    void hiddenTrafficMakesEdgeSlowerThanForecast() {
        // Prawdziwy korek × 1,5 na A→B: 60 → 90 min (prognoza o tym nie wie)
        TrafficModel traffic = new TrafficModel(Map.of(EDGE_AB, 1.5), 0, new Random(1));
        TripTimeline trip = build(A, DriverWorkState.rested(), traffic);

        assertThat(trip.actualEdgeTimeMin()).containsEntry(0, 90.0).containsEntry(1, 240.0);
        assertThat(trip.loadingStartMin()).isCloseTo(90, within(EPS));
    }

    @Test
    void firstMileIsDrivenInStraightLineToFirstNode() {
        // Kierowca 0,1° na północ od A: 11,1195 km × 1,56 = 17,346 min pierwszej mili
        GeoPoint driver = new GeoPoint(52.1, 20.0);
        TripTimeline trip = build(driver, DriverWorkState.rested(), TrafficModel.ideal());

        assertThat(trip.stateAt(0).position()).isEqualTo(driver);
        assertThat(trip.stateAt(0).edgeSeq()).isNull();
        assertThat(trip.loadingStartMin()).isCloseTo(17.346 + 60, within(1e-3));
    }

    @Test
    void tiredDriverRestsElevenHoursOnTheRoad() {
        // Dziś już 8 h 20 min: po 40 min jazdy A→B odpoczynek 11 h w 40/60 krawędzi
        TripTimeline trip = build(A, new DriverWorkState(500, 500, 0), TrafficModel.ideal());

        assertThat(trip.restCount()).isEqualTo(1);
        TripState resting = trip.stateAt(300);
        assertThat(resting.activity()).isEqualTo(Activity.REST);
        assertThat(resting.position().lon()).isCloseTo(20 + 40.0 / 60, within(1e-6));
        assertThat(trip.loadingStartMin()).isCloseTo(60 + 660, within(EPS));
    }
}
