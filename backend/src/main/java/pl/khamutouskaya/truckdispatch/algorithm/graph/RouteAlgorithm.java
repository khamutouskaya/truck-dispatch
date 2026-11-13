package pl.khamutouskaya.truckdispatch.algorithm.graph;

// Wybór algorytmu wyszukiwania trasy (parametr GET /api/routes). Oba dają trasę o tym samym czasie.
public enum RouteAlgorithm {
    DIJKSTRA,
    ASTAR;

    public RouteFinder finder() {
        return switch (this) {
            case DIJKSTRA -> new DijkstraRouteFinder();
            case ASTAR -> new AStarRouteFinder();
        };
    }
}
