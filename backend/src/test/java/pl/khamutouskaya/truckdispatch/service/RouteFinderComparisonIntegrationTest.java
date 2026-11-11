package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import pl.khamutouskaya.truckdispatch.algorithm.graph.AStarRouteFinder;
import pl.khamutouskaya.truckdispatch.algorithm.graph.DijkstraRouteFinder;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphNode;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Porównanie Dijkstra vs A* na prawdziwym grafie z migracji V5: wszystkie pary węzłów (42 × 41)
@SpringBootTest
class RouteFinderComparisonIntegrationTest {

    @Autowired
    private RoadGraphService roadGraphService;

    @Test
    void aStarFindsSameTimesAsDijkstraAndSettlesFewerNodes() {
        RoadGraph graph = roadGraphService.graph();
        DijkstraRouteFinder dijkstra = new DijkstraRouteFinder();
        AStarRouteFinder aStar = new AStarRouteFinder();
        List<VehicleProfile> vehicles = List.of(
                VehicleProfile.empty(4.0, 14000),            // wysoka, pusta — omija wiadukt 3,8 m
                VehicleProfile.loaded(3.4, 7500, 5000));     // niska, załadowana

        for (VehicleProfile vehicle : vehicles) {
            long dijkstraSettled = 0, aStarSettled = 0;
            int pairs = 0;
            for (GraphNode from : graph.nodes()) {
                for (GraphNode to : graph.nodes()) {
                    if (from.id() == to.id()) {
                        continue;
                    }
                    Optional<Route> d = dijkstra.findRoute(graph, from.id(), to.id(), vehicle);
                    Optional<Route> a = aStar.findRoute(graph, from.id(), to.id(), vehicle);

                    assertThat(a.isPresent()).isEqualTo(d.isPresent());
                    if (d.isPresent()) {
                        assertThat(a.get().totalTimeMin()).isCloseTo(d.get().totalTimeMin(), within(1e-9));
                        dijkstraSettled += d.get().settledNodeCount();
                        aStarSettled += a.get().settledNodeCount();
                    }
                    pairs++;
                }
            }
            System.out.printf("%s: %d pairs, settled nodes Dijkstra=%d, A*=%d (%.1f%%)%n",
                    vehicle, pairs, dijkstraSettled, aStarSettled, 100.0 * aStarSettled / dijkstraSettled);
            assertThat(aStarSettled).isLessThan(dijkstraSettled);
        }
    }
}
