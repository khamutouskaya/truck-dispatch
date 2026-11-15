package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/edges: cała sieć dróg z geometrią dla mapy (V4–V5: 54 drogi = 108 krawędzi).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoadEdgeApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;

    private JsonNode edges() throws Exception {
        String json = mockMvc.perform(get("/api/edges").with(TestAuth.dispatcher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json);
    }

    @Test
    void allEdgesWithGeometryFromNodeToNode() throws Exception {
        JsonNode edges = edges();
        Map<Long, RoadNode> nodes = roadNodeRepository.findAll().stream()
                .collect(Collectors.toMap(RoadNode::getId, Function.identity()));

        assertThat(edges.size()).isEqualTo(108);
        long previousId = 0;
        for (JsonNode e : edges) {
            assertThat(e.get("id").asLong()).isGreaterThan(previousId); // posortowane po id
            previousId = e.get("id").asLong();
            assertThat(e.get("roadType").asString()).isNotBlank();
            assertThat(e.get("delayFactor").asDouble()).isEqualTo(1.0);
            assertThat(e.get("distanceKm").asDouble()).isPositive();

            // [lat, lon]: linia zaczyna się przy węźle from i kończy przy węźle to (gdyby było [lon, lat] — ~30° obok)
            JsonNode line = e.get("geometryLatLon");
            assertThat(line.size()).isGreaterThanOrEqualTo(2);
            RoadNode from = nodes.get(e.get("fromNodeId").asLong());
            RoadNode to = nodes.get(e.get("toNodeId").asLong());
            assertThat(e.get("fromNodeName").asString()).isEqualTo(from.getName());
            assertThat(e.get("toNodeName").asString()).isEqualTo(to.getName());
            JsonNode first = line.get(0);
            JsonNode last = line.get(line.size() - 1);
            assertThat(first.get(0).asDouble()).isCloseTo(from.getLat(), within(0.2));
            assertThat(first.get(1).asDouble()).isCloseTo(from.getLon(), within(0.2));
            assertThat(last.get(0).asDouble()).isCloseTo(to.getLat(), within(0.2));
            assertThat(last.get(1).asDouble()).isCloseTo(to.getLon(), within(0.2));
        }
    }

    @Test
    void driverCanReadEdgesButNotWithoutToken() throws Exception {
        mockMvc.perform(get("/api/edges").with(TestAuth.driver(1, "jan.kowalski")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/edges"))
                .andExpect(status().isUnauthorized());
    }
}
