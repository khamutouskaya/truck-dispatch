package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphNode;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

// RoadGraphService zastąpiony mockiem: mały graf z ręcznie policzoną odpowiedzią
@ExtendWith(MockitoExtension.class)
class RouteServiceTest {

    private static final long A = 1, B = 2, T = 3;
    private static final VehicleProfile TRUCK = VehicleProfile.empty(4.0, 14000);

    @Mock
    private RoadGraphService roadGraphService;

    @InjectMocks
    private RouteService routeService;

    @BeforeEach
    void setUp() {
        // A i B blisko siebie (11,12 km), T daleko na północ.
        // A→T: 60 km drogi lokalnej (45 km/h) → 80 min; B→T: 50 km autostrady (80 km/h) → 37,5 min
        RoadGraph graph = new RoadGraph(
                List.of(new GraphNode(A, "A", 52.0, 20.0),
                        new GraphNode(B, "B", 52.1, 20.0),
                        new GraphNode(T, "T", 52.5, 20.0)),
                List.of(edge(10, A, T, 60, RoadType.LOCAL),
                        edge(11, B, T, 50, RoadType.MOTORWAY)));
        when(roadGraphService.graph()).thenReturn(graph);
    }

    private static GraphEdge edge(long id, long from, long to, double km, RoadType type) {
        return new GraphEdge(id, from, to, km, type, SurfaceQuality.GOOD, Terrain.FLAT, null, null, 1.0);
    }

    @Test
    void driverStandingInADrivesToBBecauseMotorwayFromBIsFaster() {
        // Kierowca stoi w A: przez A = 0 + 80 = 80 min; przez B = 17,346 (pierwsza mila 11,12 km × 1,56) + 37,5 = 54,846 min
        Route route = routeService.routeFromPosition(52.0, 20.0, T, TRUCK).orElseThrow();

        assertThat(route.startNodeId()).isEqualTo(B);
        assertThat(route.startOffsetMin()).isCloseTo(17.346, within(1e-3));
        assertThat(route.totalTimeMin()).isCloseTo(54.846, within(1e-3));
        assertThat(route.edges()).extracting(GraphEdge::id).containsExactly(11L);
        assertThat(route.distanceKm()).isEqualTo(50.0); // po grafie, bez pierwszej mili
    }

    @Test
    void routeBetweenNodesHasNoFirstMile() {
        Route route = routeService.routeBetweenNodes(A, T, TRUCK).orElseThrow();

        assertThat(route.startOffsetMin()).isZero();
        assertThat(route.totalTimeMin()).isCloseTo(80.0, within(1e-9));
    }

    @Test
    void returnsEmptyWhenTargetIsUnreachable() {
        // Z T nie wychodzi żadna krawędź
        assertThat(routeService.routeBetweenNodes(T, A, TRUCK)).isEmpty();
    }
}
