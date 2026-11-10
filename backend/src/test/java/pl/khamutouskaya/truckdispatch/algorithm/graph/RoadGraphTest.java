package pl.khamutouskaya.truckdispatch.algorithm.graph;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test jednostkowy na małym grafie ze znaną odpowiedzią, bez bazy i bez Springa
class RoadGraphTest {

    private static final long A = 1, B = 2, C = 3, D = 4;

    private static final List<GraphNode> NODES = List.of(
            new GraphNode(A, "A", 52.0, 21.0),
            new GraphNode(B, "B", 52.0, 20.0),
            new GraphNode(C, "C", 51.0, 20.0),
            new GraphNode(D, "D", 51.0, 21.0));

    // A <-> B <-> C <-> D: każda droga w obie strony
    private static final List<GraphEdge> TWO_WAY_EDGES = List.of(
            edge(10, A, B), edge(11, B, A),
            edge(12, B, C), edge(13, C, B),
            edge(14, C, D), edge(15, D, C));

    @Test
    void buildsAdjacencyList() {
        RoadGraph graph = new RoadGraph(NODES, TWO_WAY_EDGES);

        assertThat(graph.nodeCount()).isEqualTo(4);
        assertThat(graph.edgeCount()).isEqualTo(6);
        assertThat(graph.outgoingEdges(B)).extracting(GraphEdge::toId).containsExactlyInAnyOrder(A, C);
        assertThat(graph.outgoingEdges(A)).extracting(GraphEdge::toId).containsExactly(B);
        assertThat(graph.node(C).name()).isEqualTo("C");
    }

    @Test
    void twoWayChainIsStronglyConnected() {
        RoadGraph graph = new RoadGraph(NODES, TWO_WAY_EDGES);

        assertThat(graph.reachableFrom(A)).containsExactlyInAnyOrder(A, B, C, D);
        assertThat(graph.isStronglyConnected()).isTrue();
    }

    @Test
    void oneWayEdgeBreaksStrongConnectivity() {
        // C -> D tylko w jedną stronę: do D da się dojechać, ale z D — nigdzie
        RoadGraph graph = new RoadGraph(NODES, List.of(
                edge(10, A, B), edge(11, B, A),
                edge(12, B, C), edge(13, C, B),
                edge(14, C, D)));

        assertThat(graph.reachableFrom(A)).containsExactlyInAnyOrder(A, B, C, D);
        assertThat(graph.reachableFrom(D)).containsExactly(D);
        assertThat(graph.canReach(D)).containsExactlyInAnyOrder(A, B, C, D);
        assertThat(graph.canReach(A)).doesNotContain(D);
        assertThat(graph.isStronglyConnected()).isFalse();
    }

    @Test
    void isolatedNodeIsNotReachable() {
        // D nie ma żadnych dróg
        RoadGraph graph = new RoadGraph(NODES, List.of(
                edge(10, A, B), edge(11, B, A),
                edge(12, B, C), edge(13, C, B)));

        assertThat(graph.outgoingEdges(D)).isEmpty();
        assertThat(graph.reachableFrom(A)).doesNotContain(D);
        assertThat(graph.isStronglyConnected()).isFalse();
    }

    @Test
    void rejectsEdgeToUnknownNode() {
        List<GraphEdge> edges = List.of(edge(10, A, 99));

        assertThatThrownBy(() -> new RoadGraph(NODES, edges))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Edge 10");
    }

    @Test
    void rejectsDuplicateNodeId() {
        List<GraphNode> nodes = List.of(new GraphNode(A, "A", 52.0, 21.0), new GraphNode(A, "A2", 51.0, 20.0));

        assertThatThrownBy(() -> new RoadGraph(nodes, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate node id 1");
    }

    @Test
    void rejectsUnknownNodeQuery() {
        RoadGraph graph = new RoadGraph(NODES, TWO_WAY_EDGES);

        assertThatThrownBy(() -> graph.outgoingEdges(99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unknown node id 99");
    }

    @Test
    void adjacencyListCannotBeModifiedFromOutside() {
        RoadGraph graph = new RoadGraph(NODES, TWO_WAY_EDGES);

        assertThatThrownBy(() -> graph.outgoingEdges(A).add(edge(99, A, D)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static GraphEdge edge(long id, long from, long to) {
        return new GraphEdge(id, from, to, 100.0, RoadType.MOTORWAY, SurfaceQuality.GOOD, Terrain.FLAT,
                null, null, 1.0);
    }
}
