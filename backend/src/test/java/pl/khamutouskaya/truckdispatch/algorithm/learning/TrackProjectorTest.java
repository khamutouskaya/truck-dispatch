package pl.khamutouskaya.truckdispatch.algorithm.learning;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.PlannedEdge;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.TrafficModel;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.TripTimeline;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Ślad GPS generuje prawdziwy TripTimeline (próbka co 1 min), a projektor ma odtworzyć czasy krawędzi.
 * Trasa: A(52°N, 20°E) → B(21°E) [krawędź 0, 60 min], załadunek 30 min, B → C(22°E) [krawędź 1, 240 min].
 */
class TrackProjectorTest {

    private static final GeoPoint A = new GeoPoint(52.0, 20.0);
    private static final GeoPoint B = new GeoPoint(52.0, 21.0);
    private static final GeoPoint C = new GeoPoint(52.0, 22.0);
    private static final PlannedEdge AB = new PlannedEdge(0, 101, 60, List.of(A, B));
    private static final PlannedEdge BC = new PlannedEdge(1, 102, 240, List.of(B, C));

    // Tolerancja: postój zaczynający się między próbkami liczy się jako jazda do końca próbki (≤ 1 min)
    private static final double SAMPLING_TOLERANCE_MIN = 1.0;

    private static List<TrackPoint> sample(TripTimeline trip, double stepMin) {
        List<TrackPoint> track = new ArrayList<>();
        for (double t = 0; t <= trip.finishMin() + stepMin; t += stepMin) {
            track.add(new TrackPoint(trip.stateAt(t).position(), t));
        }
        return track;
    }

    private static TripTimeline trip(GeoPoint driver, TrafficModel traffic) {
        return TripTimeline.build(driver, List.of(AB), B, 30, List.of(BC), C, 30, DriverWorkState.rested(), traffic);
    }

    @Test
    void recoversDrivingTimesAndIgnoresLoadingAndBreak() {
        // Na krawędzi 1 jest przerwa 45 min — w czasie jazdy jej nie ma: 240, nie 285
        Map<Integer, Double> times = TrackProjector.drivingTimeByEdge(List.of(AB, BC),
                sample(trip(A, TrafficModel.ideal()), 1));

        assertThat(times.get(0)).isCloseTo(60, within(SAMPLING_TOLERANCE_MIN));
        assertThat(times.get(1)).isCloseTo(240, within(SAMPLING_TOLERANCE_MIN));
    }

    @Test
    void detectsHiddenCongestionFromGpsOnly() {
        // Prawdziwy korek × 1,5 na A→B — projektor nie zna TrafficModel, widzi tylko punkty
        TrafficModel traffic = new TrafficModel(Map.of(101L, 1.5), 0, new Random(1));

        Map<Integer, Double> times = TrackProjector.drivingTimeByEdge(List.of(AB, BC), sample(trip(A, traffic), 1));

        assertThat(times.get(0)).isCloseTo(90, within(SAMPLING_TOLERANCE_MIN));
        assertThat(times.get(1)).isCloseTo(240, within(SAMPLING_TOLERANCE_MIN));
    }

    @Test
    void firstMileFarFromRouteIsIgnored() {
        // Start 0,1° na północ od A (11 km): pierwsza mila jest poza korytarzem 5 km i nie psuje czasu krawędzi 0
        Map<Integer, Double> times = TrackProjector.drivingTimeByEdge(List.of(AB, BC),
                sample(trip(new GeoPoint(52.1, 20.0), TrafficModel.ideal()), 1));

        assertThat(times.get(0)).isCloseTo(60, within(SAMPLING_TOLERANCE_MIN));
    }

    @Test
    void routeReturningOnTheSameRoadIsNotConfused() {
        // A→B pusty, potem B→A z ładunkiem — ta sama droga w drugą stronę (geometria odwrócona)
        PlannedEdge back = new PlannedEdge(1, 103, 75, List.of(B, A));
        TripTimeline trip = TripTimeline.build(A, List.of(AB), B, 30, List.of(back), A, 30,
                DriverWorkState.rested(), TrafficModel.ideal());

        Map<Integer, Double> times = TrackProjector.drivingTimeByEdge(List.of(AB, back), sample(trip, 1));

        assertThat(times.get(0)).isCloseTo(60, within(SAMPLING_TOLERANCE_MIN));
        assertThat(times.get(1)).isCloseTo(75, within(SAMPLING_TOLERANCE_MIN));
    }

    @Test
    void partiallyCoveredEdgeIsNotMeasured() {
        // Ślad urywa się w połowie krawędzi 1 (np. brak GPS) — nie wiemy, ile trwała, więc jej nie uczymy
        List<TrackPoint> track = sample(trip(A, TrafficModel.ideal()), 1).stream()
                .filter(p -> p.tMin() <= 200).toList();

        Map<Integer, Double> times = TrackProjector.drivingTimeByEdge(List.of(AB, BC), track);

        assertThat(times).containsOnlyKeys(0);
    }

    @Test
    void tooShortTrackGivesNothing() {
        assertThat(TrackProjector.drivingTimeByEdge(List.of(AB), List.of(new TrackPoint(A, 0)))).isEmpty();
    }
}
