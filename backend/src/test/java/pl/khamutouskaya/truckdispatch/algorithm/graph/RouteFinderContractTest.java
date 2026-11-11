package pl.khamutouskaya.truckdispatch.algorithm.graph;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Wspólne testy dla każdej implementacji RouteFinder (Dijkstra, A*).
 * Małe grafy z odpowiedzią policzoną ręcznie; każda implementacja musi dać te same wyniki.
 * Współrzędne węzłów są dobrane tak, aby długość krawędzi nie była mniejsza niż odległość w linii prostej.
 */
abstract class RouteFinderContractTest {

    private static final double EPS = 1e-9;

    private static final VehicleProfile HIGH_EMPTY = VehicleProfile.empty(4.0, 14000);
    private static final VehicleProfile LOW_EMPTY = VehicleProfile.empty(3.4, 7500);
    private static final VehicleProfile HIGH_LOADED = VehicleProfile.loaded(4.0, 14000, 18000);

    protected abstract RouteFinder finder();

    // --- Przykład z docs/karta-i-algorytm.md, część 2.1 ---

    private static final long WARSZAWA = 1, SKIERNIEWICE = 2, LODZ = 3;

    private static RoadGraph documentationExample() {
        return new RoadGraph(
                List.of(new GraphNode(WARSZAWA, "Warszawa", 52.2297, 21.0122),
                        new GraphNode(SKIERNIEWICE, "Skierniewice", 51.9547, 20.1583),
                        new GraphNode(LODZ, "Łódź", 51.7592, 19.4560)),
                List.of(edge(10, WARSZAWA, LODZ, 135, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                        edge(11, WARSZAWA, SKIERNIEWICE, 70, RoadType.NATIONAL, SurfaceQuality.AVERAGE),
                        edge(12, SKIERNIEWICE, LODZ, 55, RoadType.REGIONAL, SurfaceQuality.GOOD)));
    }

    @Test
    void choosesFasterMotorwayAlthoughItIsLongerInKm() {
        // A2: 135 km → 101,25 min; przez Skierniewice: 125 km → 76,02 + 60 = 136,02 min
        Route route = finder().findRoute(documentationExample(), WARSZAWA, LODZ, HIGH_EMPTY).orElseThrow();

        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(10L);
        assertThat(route.distanceKm()).isCloseTo(135.0, within(EPS));
        assertThat(route.totalTimeMin()).isCloseTo(101.25, within(EPS));
        assertThat(route.startNodeId()).isEqualTo(WARSZAWA);
        assertThat(route.targetNodeId()).isEqualTo(LODZ);
    }

    @Test
    void recordsEntryTimeOfEveryEdge() {
        Route route = finder().findRoute(documentationExample(), WARSZAWA, SKIERNIEWICE, HIGH_EMPTY).orElseThrow();
        RoadGraph graph = new RoadGraph(documentationExample().nodes(), List.of(
                edge(11, WARSZAWA, SKIERNIEWICE, 70, RoadType.NATIONAL, SurfaceQuality.AVERAGE),
                edge(12, SKIERNIEWICE, LODZ, 55, RoadType.REGIONAL, SurfaceQuality.GOOD)));

        Route twoEdges = finder().findRoute(graph, WARSZAWA, LODZ, HIGH_EMPTY).orElseThrow();

        assertThat(route.edgeEntryMin()).containsExactly(0.0);
        double firstEdgeMin = 70 / 55.25 * 60;   // 76,018 min
        assertThat(twoEdges.edges()).extracting(GraphEdge::id).containsExactly(11L, 12L);
        assertThat(twoEdges.edgeEntryMin().get(0)).isCloseTo(0.0, within(EPS));
        assertThat(twoEdges.edgeEntryMin().get(1)).isCloseTo(firstEdgeMin, within(EPS));
        assertThat(twoEdges.totalTimeMin()).isCloseTo(firstEdgeMin + 60.0, within(EPS));
        assertThat(twoEdges.distanceKm()).isCloseTo(125.0, within(EPS));
    }

    // --- Ograniczenie wysokości: wiadukt i objazd ---

    private static final long A = 1, B = 2, C = 3;

    private static RoadGraph viaductGraph() {
        // A → B bezpośrednio 50 km pod wiaduktem 3,8 m; objazd A → C → B 80 + 80 km
        GraphEdge direct = new GraphEdge(10, A, B, 50, RoadType.NATIONAL, SurfaceQuality.GOOD, Terrain.FLAT,
                null, 3.8, 1.0);
        return new RoadGraph(nearbyNodes(A, B, C), List.of(direct,
                edge(11, A, C, 80, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                edge(12, C, B, 80, RoadType.MOTORWAY, SurfaceQuality.GOOD)));
    }

    @Test
    void lowVehicleGoesUnderViaduct() {
        Route route = finder().findRoute(viaductGraph(), A, B, LOW_EMPTY).orElseThrow();

        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(10L);
        assertThat(route.totalTimeMin()).isCloseTo(50 / 65.0 * 60, within(EPS));
    }

    @Test
    void highVehicleTakesDetourAroundViaduct() {
        Route route = finder().findRoute(viaductGraph(), A, B, HIGH_EMPTY).orElseThrow();

        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(11L, 12L);
        assertThat(route.totalTimeMin()).isCloseTo(120.0, within(EPS));   // 160 km / 80 km/h
    }

    @Test
    void returnsEmptyWhenOnlyRoadIsClosedForVehicle() {
        GraphEdge onlyRoad = new GraphEdge(10, A, B, 50, RoadType.NATIONAL, SurfaceQuality.GOOD, Terrain.FLAT,
                null, 3.8, 1.0);
        RoadGraph graph = new RoadGraph(nearbyNodes(A, B), List.of(onlyRoad));

        assertThat(finder().findRoute(graph, A, B, HIGH_EMPTY)).isEmpty();
    }

    @Test
    void returnsEmptyWhenTargetIsUnreachable() {
        // B → A istnieje, A → B nie (droga jednokierunkowa)
        RoadGraph graph = new RoadGraph(nearbyNodes(A, B),
                List.of(edge(10, B, A, 50, RoadType.NATIONAL, SurfaceQuality.GOOD)));

        Optional<Route> route = finder().findRoute(graph, A, B, HIGH_EMPTY);

        assertThat(route).isEmpty();
    }

    // --- Załadunek zmienia trasę w górach ---

    @Test
    void emptyTruckCrossesMountainsButLoadedTruckGoesAround() {
        // góry: 100 km drogi krajowej; pusty 65 × 0,75 → 123,08 min, załadowany 65 × 0,65 → 142,01 min
        // objazd: 175 km autostrady → 131,25 min dla obu
        GraphEdge mountain = new GraphEdge(10, A, B, 100, RoadType.NATIONAL, SurfaceQuality.GOOD,
                Terrain.MOUNTAIN, null, null, 1.0);
        RoadGraph graph = new RoadGraph(nearbyNodes(A, B, C), List.of(mountain,
                edge(11, A, C, 90, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                edge(12, C, B, 85, RoadType.MOTORWAY, SurfaceQuality.GOOD)));

        Route empty = finder().findRoute(graph, A, B, HIGH_EMPTY).orElseThrow();
        Route loaded = finder().findRoute(graph, A, B, HIGH_LOADED).orElseThrow();

        assertThat(empty.edges()).extracting(GraphEdge::id).containsExactly(10L);
        assertThat(empty.totalTimeMin()).isCloseTo(100 / 48.75 * 60, within(EPS));
        assertThat(loaded.edges()).extracting(GraphEdge::id).containsExactly(11L, 12L);
        assertThat(loaded.totalTimeMin()).isCloseTo(131.25, within(EPS));
    }

    // --- Kilka startów naraz („pierwsza mila” do trzech najbliższych węzłów) ---

    private static final long S1 = 1, S2 = 2, S3 = 3, T = 4;

    private static RoadGraph threeStartsGraph() {
        // S1 → T 120 km, S2 → T 60 km, S3 → T 100 km (autostrada, 80 km/h)
        return new RoadGraph(nearbyNodes(S1, S2, S3, T), List.of(
                edge(10, S1, T, 120, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                edge(11, S2, T, 60, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                edge(12, S3, T, 100, RoadType.MOTORWAY, SurfaceQuality.GOOD)));
    }

    @Test
    void picksBestStartIncludingFirstMile() {
        // S1: 5 + 90 = 95 min; S2: 40 + 45 = 85 min (najlepszy); S3: 12 + 75 = 87 min.
        // S2 wygrywa, choć ma najdłuższą pierwszą milę — liczy się suma.
        Map<Long, Double> starts = Map.of(S1, 5.0, S2, 40.0, S3, 12.0);

        Route route = finder().findRoute(threeStartsGraph(), starts, T, HIGH_EMPTY).orElseThrow();

        assertThat(route.startNodeId()).isEqualTo(S2);
        assertThat(route.startOffsetMin()).isEqualTo(40.0);
        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(11L);
        assertThat(route.edgeEntryMin()).containsExactly(40.0);
        assertThat(route.totalTimeMin()).isCloseTo(85.0, within(EPS));
        assertThat(route.graphTimeMin()).isCloseTo(45.0, within(EPS));
    }

    @Test
    void startNodeCanBeReachedFasterThroughAnotherStart() {
        // Start X ma pierwszą milę 30 min, ale z startu Y (0 min) dojedziemy do X w 15 min (20 km)
        long y = 1, x = 2, t = 3;
        RoadGraph graph = new RoadGraph(nearbyNodes(y, x, t), List.of(
                edge(10, y, x, 20, RoadType.MOTORWAY, SurfaceQuality.GOOD),
                edge(11, x, t, 40, RoadType.MOTORWAY, SurfaceQuality.GOOD)));

        Route route = finder().findRoute(graph, Map.of(x, 30.0, y, 0.0), t, HIGH_EMPTY).orElseThrow();

        assertThat(route.startNodeId()).isEqualTo(y);
        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(10L, 11L);
        assertThat(route.totalTimeMin()).isCloseTo(45.0, within(EPS));   // 15 + 30
    }

    @Test
    void startEqualToTargetGivesEmptyRoute() {
        Route route = finder().findRoute(threeStartsGraph(), Map.of(T, 12.0), T, HIGH_EMPTY).orElseThrow();

        assertThat(route.edges()).isEmpty();
        assertThat(route.distanceKm()).isZero();
        assertThat(route.totalTimeMin()).isEqualTo(12.0);
    }

    @Test
    void rejectsInvalidArguments() {
        RoadGraph graph = threeStartsGraph();

        assertThatThrownBy(() -> finder().findRoute(graph, Map.of(), T, HIGH_EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finder().findRoute(graph, Map.of(S1, -1.0), T, HIGH_EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> finder().findRoute(graph, S1, 99, HIGH_EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --- Pomocnicze ---

    protected static GraphEdge edge(long id, long from, long to, double km, RoadType type, SurfaceQuality surface) {
        return new GraphEdge(id, from, to, km, type, surface, Terrain.FLAT, null, null, 1.0);
    }

    // Węzły leżące blisko siebie (~1 km): linia prosta nigdy nie jest dłuższa niż krawędź
    protected static List<GraphNode> nearbyNodes(long... ids) {
        return java.util.Arrays.stream(ids)
                .mapToObj(id -> new GraphNode(id, "N" + id, 52.0 + id * 0.01, 21.0))
                .toList();
    }
}
