package pl.khamutouskaya.truckdispatch.algorithm.graph;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class FirstMileTest {

    // Pozycja kierowcy i węzły na jednym południku 20°E: 0,1° szerokości = 11,1195 km
    private static final double LAT = 52.0, LON = 20.0;

    private static RoadGraph meridianGraph() {
        return new RoadGraph(List.of(
                new GraphNode(1, "N1", 52.1, LON),    // 11,1195 km
                new GraphNode(2, "N2", 52.2, LON),    // 22,2390 km
                new GraphNode(3, "N3", 52.3, LON),    // 33,3585 km
                new GraphNode(4, "N4", 52.4, LON),    // 44,4780 km
                new GraphNode(5, "N5", 51.95, LON)),  // 5,5597 km — najbliższy
                List.of());
    }

    @Test
    void timeIsStraightKmTimes1point56() {
        // 1,3 / 50 km/h × 60 = 1,56 min na 1 km w linii prostej
        assertThat(FirstMile.timeMin(10.0)).isCloseTo(15.6, within(1e-9));
        assertThat(FirstMile.roadKm(10.0)).isCloseTo(13.0, within(1e-9));
    }

    @Test
    void picksThreeNearestNodesSortedByDistanceWithFirstMileTime() {
        Map<Long, Double> sources = FirstMile.startNodes(meridianGraph(), LAT, LON, 3);

        assertThat(sources.keySet()).containsExactly(5L, 1L, 2L);
        assertThat(sources.get(5L)).isCloseTo(5.55975 * 1.56, within(1e-3));   //  8,673 min
        assertThat(sources.get(1L)).isCloseTo(11.11949 * 1.56, within(1e-3));  // 17,346 min
        assertThat(sources.get(2L)).isCloseTo(22.23899 * 1.56, within(1e-3));  // 34,693 min
    }

    @Test
    void driverStandingOnNodeHasZeroFirstMile() {
        Map<Long, Double> sources = FirstMile.startNodes(meridianGraph(), 52.1, LON, 3);

        assertThat(sources.keySet().iterator().next()).isEqualTo(1L);
        assertThat(sources.get(1L)).isZero();
    }

    @Test
    void equalDistanceIsResolvedBySmallerNodeId() {
        // Węzły 7 i 6 w tej samej odległości (na północ i na południe) — wygrywa mniejsze id
        RoadGraph graph = new RoadGraph(List.of(
                new GraphNode(7, "North", 52.1, LON),
                new GraphNode(6, "South", 51.9, LON)), List.of());

        assertThat(FirstMile.startNodes(graph, LAT, LON, 1).keySet()).containsExactly(6L);
    }

    @Test
    void returnsAllNodesWhenGraphIsSmallerThanCount() {
        RoadGraph graph = new RoadGraph(List.of(new GraphNode(1, "Only", 52.1, LON)), List.of());

        assertThat(FirstMile.startNodes(graph, LAT, LON, 3)).containsOnlyKeys(1L);
    }

    @Test
    void rejectsInvalidArguments() {
        assertThatThrownBy(() -> FirstMile.startNodes(meridianGraph(), LAT, LON, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FirstMile.startNodes(new RoadGraph(List.of(), List.of()), LAT, LON, 3))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
