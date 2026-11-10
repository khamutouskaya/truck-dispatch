package pl.khamutouskaya.truckdispatch.algorithm.graph;

// Wierzchołek grafu w pamięci: miasto lub węzeł drogowy
public record GraphNode(
        long id,
        String name,
        double lat,
        double lon
) {
}
