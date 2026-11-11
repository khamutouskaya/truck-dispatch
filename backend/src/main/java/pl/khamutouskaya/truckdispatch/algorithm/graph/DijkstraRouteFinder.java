package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Algorytm Dijkstry z kolejką priorytetową; waga krawędzi = czas przejazdu [min] dla danego pojazdu.
 * <p>
 * Idea: zawsze rozwijamy węzeł o najmniejszym znanym czasie dotarcia. Ponieważ wagi są nieujemne,
 * w chwili wyjęcia węzła z kolejki jego czas jest już ostateczny (węzeł „zamknięty”).
 * Kończymy, gdy z kolejki wyjdzie cel. Złożoność O((V + E) log V).
 * <p>
 * Kolejka z „leniwym usuwaniem”: przy poprawie czasu nie aktualizujemy starego wpisu,
 * tylko dodajemy nowy; nieaktualne wpisy pomijamy przy wyjmowaniu.
 */
public final class DijkstraRouteFinder implements RouteFinder {

    // Wpis w kolejce: węzeł i czas, z jakim został do niej dodany
    private record QueueEntry(long nodeId, double timeMin) {
    }

    @Override
    public Optional<Route> findRoute(RoadGraph graph, Map<Long, Double> sources, long target, VehicleProfile vehicle) {
        validate(graph, sources, target);

        Map<Long, Double> bestTime = new HashMap<>();      // najlepszy znany czas dotarcia do węzła [min]
        Map<Long, GraphEdge> previousEdge = new HashMap<>(); // krawędź, którą dotarliśmy (do odtworzenia trasy)
        Set<Long> settled = new HashSet<>();                 // węzły z ostatecznym czasem
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::timeMin));

        // Wszystkie starty trafiają do kolejki od razu, każdy ze swoim czasem początkowym
        sources.forEach((nodeId, timeMin) -> {
            bestTime.put(nodeId, timeMin);
            queue.add(new QueueEntry(nodeId, timeMin));
        });

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            long current = entry.nodeId();
            if (entry.timeMin() > bestTime.get(current)) {
                continue; // nieaktualny wpis: później znaleźliśmy szybszą drogę do tego węzła
            }
            settled.add(current);
            if (current == target) {
                return Optional.of(buildRoute(sources, target, bestTime, previousEdge, settled.size()));
            }

            for (GraphEdge edge : graph.outgoingEdges(current)) {
                if (EdgeTimeCalculator.isClosedFor(edge, vehicle) || settled.contains(edge.toId())) {
                    continue;
                }
                double candidate = bestTime.get(current) + EdgeTimeCalculator.travelTimeMin(edge, vehicle);
                Double known = bestTime.get(edge.toId());
                if (known == null || candidate < known) {   // relaksacja krawędzi
                    bestTime.put(edge.toId(), candidate);
                    previousEdge.put(edge.toId(), edge);
                    queue.add(new QueueEntry(edge.toId(), candidate));
                }
            }
        }
        return Optional.empty(); // kolejka pusta, a cel nie został osiągnięty
    }

    // Odtwarza trasę od celu wstecz po previousEdge, aż do węzła bez poprzednika (jednego ze startów)
    static Route buildRoute(Map<Long, Double> sources, long target, Map<Long, Double> bestTime,
                            Map<Long, GraphEdge> previousEdge, int settledCount) {
        List<GraphEdge> edges = new ArrayList<>();
        long node = target;
        while (previousEdge.containsKey(node)) {
            GraphEdge edge = previousEdge.get(node);
            edges.add(edge);
            node = edge.fromId();
        }
        Collections.reverse(edges);
        long start = node;

        List<Double> entryMin = new ArrayList<>(edges.size());
        double distanceKm = 0;
        for (GraphEdge edge : edges) {
            entryMin.add(bestTime.get(edge.fromId())); // czas wjazdu na krawędź = czas dotarcia do jej początku
            distanceKm += edge.distanceKm();
        }
        return new Route(start, target, sources.get(start), edges, entryMin, distanceKm,
                bestTime.get(target), settledCount);
    }

    static void validate(RoadGraph graph, Map<Long, Double> sources, long target) {
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("At least one source node is required");
        }
        sources.forEach((nodeId, timeMin) -> {
            graph.node(nodeId); // wyjątek dla nieznanego węzła
            if (timeMin == null || timeMin < 0) {
                throw new IllegalArgumentException("Start time for node " + nodeId + " must be non-negative");
            }
        });
        graph.node(target);
    }
}
