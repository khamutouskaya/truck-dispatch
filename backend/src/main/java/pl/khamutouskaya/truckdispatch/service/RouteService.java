package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.khamutouskaya.truckdispatch.algorithm.graph.DijkstraRouteFinder;
import pl.khamutouskaya.truckdispatch.algorithm.graph.FirstMile;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RouteFinder;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;

import java.util.Map;
import java.util.Optional;

/**
 * Spring-owa obudowa wyszukiwania tras: bierze graf z RoadGraphService i wywołuje Dijkstrę.
 * Cała logika jest w pakiecie algorithm; tu tylko składanie elementów.
 * Bez @Transactional — graf jest w pamięci, baza nie jest używana.
 */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RoadGraphService roadGraphService;

    // Dijkstra — algorytm podstawowy; A* daje te same czasy i służy do porównania (RouteFinderComparisonIntegrationTest)
    private final RouteFinder routeFinder = new DijkstraRouteFinder();

    /**
     * Trasa z dowolnego punktu (np. pozycji kierowcy) do węzła docelowego:
     * pierwsza mila do 3 najbliższych węzłów, potem najszybsza droga po grafie.
     */
    public Optional<Route> routeFromPosition(double lat, double lon, long targetNodeId, VehicleProfile vehicle) {
        RoadGraph graph = roadGraphService.graph();
        Map<Long, Double> sources = FirstMile.startNodes(graph, lat, lon, FirstMile.NEAREST_NODE_COUNT);
        return routeFinder.findRoute(graph, sources, targetNodeId, vehicle);
    }

    // Trasa między dwoma węzłami grafu (np. załadunek → rozładunek), bez pierwszej mili
    public Optional<Route> routeBetweenNodes(long fromNodeId, long toNodeId, VehicleProfile vehicle) {
        return routeFinder.findRoute(roadGraphService.graph(), fromNodeId, toNodeId, vehicle);
    }
}
