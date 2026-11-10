package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.Terrain;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// Test jednostkowy: repozytoria zastąpione mockami, sprawdzamy mapowanie encji i kontrolę spójności
@ExtendWith(MockitoExtension.class)
class RoadGraphServiceTest {

    @Mock
    private RoadNodeRepository roadNodeRepository;

    @Mock
    private RoadEdgeRepository roadEdgeRepository;

    @InjectMocks
    private RoadGraphService roadGraphService;

    private final RoadNode warszawa = node(1L, "Warszawa");
    private final RoadNode lodz = node(2L, "Łódź");
    private final RoadNode krakow = node(3L, "Kraków");

    @Test
    void loadsGraphAndMapsEdgeAttributes() {
        RoadEdge limited = edge(10L, warszawa, lodz);
        limited.setTerrain(Terrain.HILLY);
        limited.setMaxHeightM(3.8);
        limited.setDelayFactor(1.2);
        when(roadNodeRepository.findAll()).thenReturn(List.of(warszawa, lodz));
        when(roadEdgeRepository.findAll()).thenReturn(List.of(limited, edge(11L, lodz, warszawa)));

        roadGraphService.reload();

        RoadGraph graph = roadGraphService.graph();
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.edgeCount()).isEqualTo(2);
        GraphEdge mapped = graph.outgoingEdges(1L).getFirst();
        assertThat(mapped.toId()).isEqualTo(2L);
        assertThat(mapped.distanceKm()).isEqualTo(135.0);
        assertThat(mapped.terrain()).isEqualTo(Terrain.HILLY);
        assertThat(mapped.maxHeightM()).isEqualTo(3.8);
        assertThat(mapped.maxWeightT()).isNull();
        assertThat(mapped.delayFactor()).isEqualTo(1.2);
    }

    @Test
    void rejectsGraphThatIsNotStronglyConnected() {
        // Kraków nie ma żadnych dróg
        when(roadNodeRepository.findAll()).thenReturn(List.of(warszawa, lodz, krakow));
        when(roadEdgeRepository.findAll()).thenReturn(List.of(edge(10L, warszawa, lodz), edge(11L, lodz, warszawa)));

        assertThatThrownBy(() -> roadGraphService.reload())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not strongly connected")
                .hasMessageContaining("Kraków");
    }

    @Test
    void graphIsUnavailableBeforeLoading() {
        assertThatThrownBy(() -> roadGraphService.graph())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Road graph is not loaded yet");
    }

    private static RoadNode node(Long id, String name) {
        RoadNode node = new RoadNode();
        node.setId(id);
        node.setName(name);
        return node;
    }

    private static RoadEdge edge(Long id, RoadNode from, RoadNode to) {
        RoadEdge edge = new RoadEdge();
        edge.setId(id);
        edge.setFromNode(from);
        edge.setToNode(to);
        edge.setDistanceKm(135.0);
        edge.setRoadType(RoadType.MOTORWAY);
        return edge;
    }
}
