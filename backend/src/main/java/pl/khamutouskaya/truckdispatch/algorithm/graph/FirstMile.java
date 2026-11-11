package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * „Pierwsza mila”: dojazd z dowolnego punktu (np. pozycji kierowcy) do grafu dróg.
 * <p>
 * Punkt dowiązujemy do kilku najbliższych węzłów (haversine). Dla każdego czas dojazdu:
 * odległość w linii prostej × 1,3 / 50 km/h (1,3 — poprawka na to, że drogi nie są proste;
 * 50 km/h — drogi lokalne, wyjazd z miasta). Wszystkie węzły trafiają do kolejki Dijkstry naraz,
 * a algorytm sam wybiera, przez który wyjechać.
 */
public final class FirstMile {

    public static final double ROAD_FACTOR = 1.3;
    public static final double SPEED_KMH = 50.0;
    public static final int NEAREST_NODE_COUNT = 3;

    private FirstMile() {
    }

    // Przybliżona długość drogi dojazdowej [km]
    public static double roadKm(double straightKm) {
        return straightKm * ROAD_FACTOR;
    }

    // Czas dojazdu [min]: 1 km w linii prostej = 1,3 / 50 × 60 = 1,56 min
    public static double timeMin(double straightKm) {
        return roadKm(straightKm) / SPEED_KMH * 60.0;
    }

    /**
     * Najbliższe węzły grafu z czasem pierwszej mili — gotowe „sources” dla RouteFinder.
     * Przy równej odległości decyduje mniejsze id (wynik powtarzalny).
     *
     * @return mapa węzeł → czas dojazdu [min], posortowana od najbliższego
     */
    public static Map<Long, Double> startNodes(RoadGraph graph, double lat, double lon, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("Node count must be positive");
        }
        if (graph.nodeCount() == 0) {
            throw new IllegalArgumentException("Graph has no nodes");
        }
        List<GraphNode> nearest = graph.nodes().stream()
                .sorted(Comparator.comparingDouble((GraphNode n) -> GeoDistance.haversineKm(lat, lon, n.lat(), n.lon()))
                        .thenComparingLong(GraphNode::id))
                .limit(count)
                .toList();

        Map<Long, Double> sources = new LinkedHashMap<>();
        for (GraphNode node : nearest) {
            sources.put(node.id(), timeMin(GeoDistance.haversineKm(lat, lon, node.lat(), node.lon())));
        }
        return sources;
    }
}
