package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;

import static org.assertj.core.api.Assertions.assertThat;

// Test integracyjny na prawdziwej bazie z migracjami V1–V5 (wymaga uruchomionego PostgreSQL w Dockerze)
@SpringBootTest
class RoadGraphServiceIntegrationTest {

    @Autowired
    private RoadGraphService roadGraphService;

    @Autowired
    private RoadEdgeRepository roadEdgeRepository;

    @Test
    void graphFromMigrationsIsLoadedAndStronglyConnected() {
        RoadGraph graph = roadGraphService.graph();

        assertThat(graph.nodeCount()).isEqualTo(42);
        assertThat(graph.edgeCount()).isEqualTo(108);
        assertThat(graph.isStronglyConnected()).isTrue();
    }

    @Test
    @Transactional(readOnly = true)
    void edgeGeometryIsReadFromJsonbColumn() {
        RoadEdge edge = roadEdgeRepository.findAll().getFirst();

        // Tablica punktów [[lon, lat], ...] zapisana przez skrypt build_graph.py
        assertThat(edge.getGeometry()).startsWith("[[").endsWith("]]");
    }
}
