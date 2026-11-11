package pl.khamutouskaya.truckdispatch.algorithm.graph;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// A* musi przejść wszystkie wspólne testy z RouteFinderContractTest, plus testy samej heurystyki
class AStarRouteFinderTest extends RouteFinderContractTest {

    private static final double EPS = 1e-9;
    private static final VehicleProfile TRUCK = VehicleProfile.empty(4.0, 14000);

    @Override
    protected RouteFinder finder() {
        return new AStarRouteFinder();
    }

    private static GraphEdge motorway(long id, long from, long to, double km, double delayFactor) {
        return new GraphEdge(id, from, to, km, RoadType.MOTORWAY, SurfaceQuality.GOOD, Terrain.FLAT,
                null, null, delayFactor);
    }

    // Węzły na jednym równoleżniku 52°N, co 0,5° długości (≈ 34,24 km w linii prostej)
    private static GraphNode node(long id, double lon) {
        return new GraphNode(id, "N" + id, 52.0, lon);
    }

    @Test
    void settlesFewerNodesThanDijkstraWhenGraphExtendsAwayFromTarget() {
        // W2 — W1 — C — E1 — T. Start w C, cel T na wschodzie.
        // Krawędzie na zachód są krótsze (30 km = 22,5 min), na wschód 40 km = 30 min.
        // Dijkstra zamyka wszystko, co ma czas < 60: C(0), W1(22,5), E1(30), W2(45), T(60) → 5 węzłów.
        // A* widzi, że zachód oddala od celu: f(W1) = 22,5 + 67,5 = 90 > 60 → zamyka tylko C, E1, T.
        long w2 = 1, w1 = 2, c = 3, e1 = 4, t = 5;
        RoadGraph graph = new RoadGraph(
                List.of(node(w2, 19.0), node(w1, 19.5), node(c, 20.0), node(e1, 20.5), node(t, 21.0)),
                List.of(motorway(1, c, w1, 30, 1.0), motorway(2, w1, w2, 30, 1.0),
                        motorway(3, c, e1, 40, 1.0), motorway(4, e1, t, 40, 1.0)));

        Route dijkstra = new DijkstraRouteFinder().findRoute(graph, c, t, TRUCK).orElseThrow();
        Route aStar = new AStarRouteFinder().findRoute(graph, c, t, TRUCK).orElseThrow();

        assertThat(aStar.totalTimeMin()).isCloseTo(60.0, within(EPS));
        assertThat(dijkstra.totalTimeMin()).isCloseTo(60.0, within(EPS));
        assertThat(dijkstra.settledNodeCount()).isEqualTo(5);
        assertThat(aStar.settledNodeCount()).isEqualTo(3);
    }

    @Test
    void staysOptimalWhenLearnedDelayFactorMakesEdgeFasterThan80KmhInStraightLine() {
        // S — A — T na jednym równoleżniku. Po samouczeniu:
        //   S→A 35 km, delayFactor 0,5 → 13,125 min;  A→T 35 km, delayFactor 0,1 → 2,625 min;  razem 15,75 min
        //   S→T 70 km, delayFactor 0,4 → 21 min
        // Heurystyka „prosta / 80 km/h” daje h(A) ≈ 25,7 min > rzeczywiste 2,625 min (przeszacowanie!):
        // f(A) ≈ 38,8 > 21, więc A* z taką heurystyką wyjąłby T przez S→T i zwrócił 21 min zamiast 15,75.
        long s = 1, a = 2, t = 3;
        RoadGraph graph = new RoadGraph(
                List.of(node(s, 20.0), node(a, 20.5), node(t, 21.0)),
                List.of(motorway(1, s, a, 35, 0.5), motorway(2, a, t, 35, 0.1), motorway(3, s, t, 70, 0.4)));

        double naiveHeuristicMin = GeoDistance.haversineKm(graph.node(a), graph.node(t)) / 80.0 * 60.0;
        assertThat(naiveHeuristicMin).isGreaterThan(2.625); // stałe 80 km/h nie jest dopuszczalne

        Route route = new AStarRouteFinder().findRoute(graph, s, t, TRUCK).orElseThrow();

        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(1L, 2L);
        assertThat(route.totalTimeMin()).isCloseTo(15.75, within(EPS));
    }

    @Test
    void heuristicNeverOverestimatesRemainingTime() {
        // Ten sam graf: h(n) ≤ rzeczywisty najkrótszy czas z n do celu, dla każdego węzła
        long s = 1, a = 2, t = 3;
        RoadGraph graph = new RoadGraph(
                List.of(node(s, 20.0), node(a, 20.5), node(t, 21.0)),
                List.of(motorway(1, s, a, 35, 0.5), motorway(2, a, t, 35, 0.1), motorway(3, s, t, 70, 0.4)));
        double vMax = AStarRouteFinder.maxStraightLineSpeedKmPerMin(graph, TRUCK);
        DijkstraRouteFinder dijkstra = new DijkstraRouteFinder();

        for (GraphNode n : graph.nodes()) {
            double trueTimeMin = dijkstra.findRoute(graph, n.id(), t, TRUCK).orElseThrow().totalTimeMin();
            assertThat(AStarRouteFinder.heuristicMin(n, graph.node(t), vMax)).isLessThanOrEqualTo(trueTimeMin + EPS);
        }
    }

    @Test
    void haversineOfOneDegreeOfLatitudeIsAbout111Km() {
        // 1° wzdłuż południka = 2πR / 360 = 111,195 km
        assertThat(GeoDistance.haversineKm(52.0, 20.0, 53.0, 20.0)).isCloseTo(111.195, within(0.001));
        assertThat(GeoDistance.haversineKm(52.0, 20.0, 52.0, 20.0)).isZero();
    }
}
