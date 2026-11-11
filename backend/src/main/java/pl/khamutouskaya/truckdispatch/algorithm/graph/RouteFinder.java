package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.Map;
import java.util.Optional;

/**
 * Wyszukiwanie najszybszej trasy (waga krawędzi = czas przejazdu dla danego pojazdu).
 * Implementacje: Dijkstra i A* — dla tych samych danych muszą dawać trasę o tym samym czasie.
 */
public interface RouteFinder {

    /**
     * @param graph   graf dróg
     * @param sources węzły startowe z czasem dotarcia do nich [min]; kilka startów naraz —
     *                np. trzy najbliższe węzły z czasem „pierwszej mili”; algorytm sam wybiera najlepszy
     * @param target  węzeł docelowy
     * @param vehicle profil pojazdu (wysokość, masa, załadunek)
     * @return najszybsza trasa albo {@code Optional.empty()}, jeśli cel jest nieosiągalny dla tego pojazdu
     */
    Optional<Route> findRoute(RoadGraph graph, Map<Long, Double> sources, long target, VehicleProfile vehicle);

    // Wygodny wariant z jednym startem bez pierwszej mili
    default Optional<Route> findRoute(RoadGraph graph, long source, long target, VehicleProfile vehicle) {
        return findRoute(graph, Map.of(source, 0.0), target, vehicle);
    }
}
