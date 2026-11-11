package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Algorytm A*: jak Dijkstra, ale kolejność rozwijania węzłów wg f(n) = g(n) + h(n), gdzie
 * g(n) — znany czas dotarcia do n [min], h(n) — dolne oszacowanie czasu z n do celu [min].
 * <p>
 * Heurystyka: h(n) = odległość w linii prostej (haversine) z n do celu / vMax, gdzie
 * vMax = największa „prędkość w linii prostej” na krawędziach grafu dla danego pojazdu:
 * max po krawędziach z haversine(początek, koniec) / czas przejazdu krawędzi.
 * <p>
 * Dlaczego nie stałe 80 km/h: przy delayFactor &lt; 1 (po samouczeniu) albo gdy droga jest
 * krótsza niż odległość węzłów w linii prostej, krawędź może być „szybsza” niż 80 km/h po prostej —
 * wtedy h przeszacowuje i A* mógłby zwrócić trasę, która nie jest najszybsza.
 * <p>
 * vMax liczony z danych daje heurystykę spójną (monotoniczną): dla każdej krawędzi u→v
 * czas(u→v) ≥ haversine(u,v) / vMax (z definicji vMax), a z nierówności trójkąta
 * haversine(u,cel) ≤ haversine(u,v) + haversine(v,cel), więc h(u) ≤ czas(u→v) + h(v).
 * Spójność ⇒ dopuszczalność (h nigdy nie przeszacowuje) i węzeł wyjęty z kolejki ma czas ostateczny,
 * tak jak w Dijkstrze. Dlatego A* zwraca trasę o tym samym czasie, zwykle zamykając mniej węzłów.
 */
public final class AStarRouteFinder implements RouteFinder {

    // Wpis w kolejce: węzeł, jego g w chwili dodania i priorytet f = g + h
    private record QueueEntry(long nodeId, double timeMin, double priorityMin) {
    }

    @Override
    public Optional<Route> findRoute(RoadGraph graph, Map<Long, Double> sources, long target, VehicleProfile vehicle) {
        DijkstraRouteFinder.validate(graph, sources, target);

        GraphNode targetNode = graph.node(target);
        double maxSpeedKmPerMin = maxStraightLineSpeedKmPerMin(graph, vehicle);

        Map<Long, Double> bestTime = new HashMap<>();
        Map<Long, GraphEdge> previousEdge = new HashMap<>();
        Set<Long> settled = new HashSet<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::priorityMin));

        sources.forEach((nodeId, timeMin) -> {
            bestTime.put(nodeId, timeMin);
            queue.add(new QueueEntry(nodeId, timeMin, timeMin + heuristicMin(graph.node(nodeId), targetNode, maxSpeedKmPerMin)));
        });

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            long current = entry.nodeId();
            if (entry.timeMin() > bestTime.get(current)) {
                continue; // nieaktualny wpis
            }
            settled.add(current);
            if (current == target) {
                return Optional.of(DijkstraRouteFinder.buildRoute(sources, target, bestTime, previousEdge, settled.size()));
            }

            for (GraphEdge edge : graph.outgoingEdges(current)) {
                if (EdgeTimeCalculator.isClosedFor(edge, vehicle) || settled.contains(edge.toId())) {
                    continue;
                }
                double candidate = bestTime.get(current) + EdgeTimeCalculator.travelTimeMin(edge, vehicle);
                Double known = bestTime.get(edge.toId());
                if (known == null || candidate < known) {
                    bestTime.put(edge.toId(), candidate);
                    previousEdge.put(edge.toId(), edge);
                    double h = heuristicMin(graph.node(edge.toId()), targetNode, maxSpeedKmPerMin);
                    queue.add(new QueueEntry(edge.toId(), candidate, candidate + h));
                }
            }
        }
        return Optional.empty();
    }

    // h(n) [min]: odległość w linii prostej do celu / największa prędkość w linii prostej
    static double heuristicMin(GraphNode node, GraphNode target, double maxSpeedKmPerMin) {
        if (maxSpeedKmPerMin <= 0) {
            return 0; // brak informacji — A* zachowuje się jak Dijkstra
        }
        return GeoDistance.haversineKm(node, target) / maxSpeedKmPerMin;
    }

    /**
     * vMax [km/min] = max po krawędziach z haversine(początek, koniec) / czas przejazdu krawędzi.
     * Liczone dla konkretnego pojazdu, bo czas krawędzi zależy od załadunku. Krawędzie zamknięte
     * też są brane pod uwagę: mogą tylko zwiększyć vMax, a większe vMax nie psuje dopuszczalności.
     * Koszt O(E) na zapytanie — przy ~100 krawędziach pomijalny.
     */
    static double maxStraightLineSpeedKmPerMin(RoadGraph graph, VehicleProfile vehicle) {
        double max = 0;
        for (GraphNode node : graph.nodes()) {
            for (GraphEdge edge : graph.outgoingEdges(node.id())) {
                double straightKm = GeoDistance.haversineKm(graph.node(edge.fromId()), graph.node(edge.toId()));
                double speed = straightKm / EdgeTimeCalculator.travelTimeMin(edge, vehicle);
                max = Math.max(max, speed);
            }
        }
        return max;
    }
}
