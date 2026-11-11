package pl.khamutouskaya.truckdispatch.algorithm.graph;

import java.util.List;

/**
 * Wynik wyszukiwania trasy w grafie.
 *
 * @param startNodeId      węzeł, od którego zaczęła się jazda po grafie (jeden ze startów)
 * @param targetNodeId     węzeł docelowy
 * @param startOffsetMin   czas przed wjazdem na graf [min], np. „pierwsza mila” do wybranego węzła startowego
 * @param edges            kolejne krawędzie trasy (pusta lista, jeśli start = cel)
 * @param edgeEntryMin     moment wjazdu na każdą krawędź [min od początku podróży, łącznie z startOffsetMin];
 *                         potrzebne do symulacji przerw (UE 561/2006) i przyszłych ograniczeń zależnych od czasu
 * @param distanceKm       długość trasy po grafie [km] (bez pierwszej mili)
 * @param totalTimeMin     czas całkowity [min] = startOffsetMin + suma czasów krawędzi
 * @param settledNodeCount liczba węzłów zamkniętych przez algorytm — statystyka do porównania Dijkstra vs A*
 */
public record Route(
        long startNodeId,
        long targetNodeId,
        double startOffsetMin,
        List<GraphEdge> edges,
        List<Double> edgeEntryMin,
        double distanceKm,
        double totalTimeMin,
        int settledNodeCount
) {
    public Route {
        edges = List.copyOf(edges);
        edgeEntryMin = List.copyOf(edgeEntryMin);
    }

    // Czas jazdy po samym grafie [min], bez pierwszej mili
    public double graphTimeMin() {
        return totalTimeMin - startOffsetMin;
    }
}
