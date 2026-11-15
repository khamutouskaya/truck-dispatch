package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.service.SimulationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/orders/{id}/track: zlecenie Warszawa → Gdańsk (przydzielone Janowi), symulator przesuwa je
 * czterema krokami po 30 min — w historii GPS powstają 4 punkty (start + 3 kroki).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderTrackApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private SimulationService simulationService;

    private long nodeId(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name))
                .map(RoadNode::getId).findFirst().orElseThrow();
    }

    private long createOrder() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String body = """
                {"loadingNodeId": %d, "unloadingNodeId": %d, "cargoWeightKg": 7000, "cargoVolumeM3": 30,
                 "requiredVehicleType": "TILT", "loadingDeadline": "%s", "deliveryDeadline": "%s",
                 "estimatedLoadingMin": 60, "estimatedUnloadingMin": 60}
                """.formatted(nodeId("Warszawa"), nodeId("Gdańsk"), now.plusHours(6), now.plusHours(30));
        String json = mockMvc.perform(post("/api/orders").with(TestAuth.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asLong();
    }

    private JsonNode track(long orderId) throws Exception {
        String json = mockMvc.perform(get("/api/orders/{id}/track", orderId).with(TestAuth.dispatcher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json);
    }

    @Test
    void trackGrowsAsSimulatorMovesDriver() throws Exception {
        long orderId = createOrder();

        // Przed pierwszym krokiem symulatora — ślad pusty
        JsonNode empty = track(orderId);
        assertThat(empty.get("driverId").asLong()).isEqualTo(1);
        assertThat(empty.get("points").size()).isZero();

        for (int i = 0; i < 4; i++) {
            simulationService.advance(30);
        }

        JsonNode track = track(orderId);
        assertThat(track.get("orderId").asLong()).isEqualTo(orderId);
        assertThat(track.get("status").asString()).isIn("ASSIGNED", "IN_PROGRESS");
        JsonNode points = track.get("points");
        assertThat(points.size()).isEqualTo(4);

        // Start = położenie Jana (Warszawa, V3); kolejne punkty co 30 min czasu symulacji
        assertThat(points.get(0).get("lat").asDouble()).isCloseTo(52.2297, within(1e-6));
        assertThat(points.get(0).get("lon").asDouble()).isCloseTo(21.0122, within(1e-6));
        for (int i = 1; i < points.size(); i++) {
            LocalDateTime previous = LocalDateTime.parse(points.get(i - 1).get("recordedAt").asString());
            LocalDateTime current = LocalDateTime.parse(points.get(i).get("recordedAt").asString());
            assertThat(Duration.between(previous, current)).isEqualTo(Duration.ofMinutes(30));
            assertThat(points.get(i).get("source").asString()).isEqualTo("SIMULATION");
        }
        // Jedzie na północ, w stronę Gdańska
        assertThat(points.get(3).get("lat").asDouble()).isGreaterThan(points.get(0).get("lat").asDouble());
    }

    @Test
    void unknownOrderIs404AndDriverHasNoAccess() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/track", 999_999).with(TestAuth.dispatcher()))
                .andExpect(status().isNotFound());
        // Zlecenie z V3 (NEW, bez kierowcy): pusty ślad, driverId null
        mockMvc.perform(get("/api/orders/{id}/track", 1).with(TestAuth.dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").isEmpty())
                .andExpect(jsonPath("$.points").isEmpty());
        // Tylko dyspozytor
        mockMvc.perform(get("/api/orders/{id}/track", 1).with(TestAuth.driver(1, "jan.kowalski")))
                .andExpect(status().isForbidden());
    }
}
