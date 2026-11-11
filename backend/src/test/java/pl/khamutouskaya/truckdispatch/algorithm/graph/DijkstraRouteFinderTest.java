package pl.khamutouskaya.truckdispatch.algorithm.graph;

// Dijkstra musi przejść wszystkie wspólne testy z RouteFinderContractTest
class DijkstraRouteFinderTest extends RouteFinderContractTest {

    @Override
    protected RouteFinder finder() {
        return new DijkstraRouteFinder();
    }
}
