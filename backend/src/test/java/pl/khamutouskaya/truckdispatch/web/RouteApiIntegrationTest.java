package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;
import pl.khamutouskaya.truckdispatch.service.RoadGraphService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/routes na prawdziwym grafie z bazy.
 * Wiadukt Piła ↔ Bydgoszcz (max 3,8 m, V5): niski pojazd LU 89012 (3,4 m) przejeżdża, WX 12345 (4,0 m) — nie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RouteApiIntegrationTest {

    private static final String LOW_VEHICLE = "LU 89012";
    private static final String HIGH_VEHICLE = "WX 12345";

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private RoadEdgeRepository roadEdgeRepository;
    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private RoadGraphService roadGraphService;

    // Test 409 zmienia ograniczenia krawędzi i przeładowuje graf; po wycofaniu transakcji — graf z czystej bazy
    @AfterTransaction
    void reloadGraphFromCleanDatabase() {
        roadGraphService.reload();
    }

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    private long vehicleId(String registration) {
        return vehicleRepository.findAll().stream().filter(v -> v.getRegistrationNumber().equals(registration))
                .map(Vehicle::getId).findFirst().orElseThrow();
    }

    private JsonNode route(String from, String to, String registration, String algorithm) throws Exception {
        String json = mockMvc.perform(get("/api/routes")
                        .param("fromNodeId", String.valueOf(node(from).getId()))
                        .param("toNodeId", String.valueOf(node(to).getId()))
                        .param("vehicleId", String.valueOf(vehicleId(registration)))
                        .param("algorithm", algorithm))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json);
    }

    private static List<String> nodeNames(JsonNode route) {
        List<String> names = new ArrayList<>();
        route.get("nodes").forEach(n -> names.add(n.get("name").asString()));
        return names;
    }

    @Test
    void routeIsConsistentFromNodesToGeometry() throws Exception {
        JsonNode route = route("Warszawa", "Gdańsk", HIGH_VEHICLE, "ASTAR");

        assertThat(route.get("algorithm").asString()).isEqualTo("ASTAR");
        assertThat(route.get("loaded").asBoolean()).isTrue(); // domyślnie
        assertThat(nodeNames(route)).containsExactly("Warszawa", "Ostróda", "Elbląg", "Gdańsk");

        // Krawędzie łączą kolejne węzły; suma km i minut = wartości całej trasy
        JsonNode nodes = route.get("nodes");
        JsonNode edges = route.get("edges");
        assertThat(edges.size()).isEqualTo(nodes.size() - 1);
        double km = 0;
        double min = 0;
        for (int i = 0; i < edges.size(); i++) {
            JsonNode e = edges.get(i);
            assertThat(e.get("fromNodeId").asLong()).isEqualTo(nodes.get(i).get("id").asLong());
            assertThat(e.get("toNodeId").asLong()).isEqualTo(nodes.get(i + 1).get("id").asLong());
            assertThat(e.get("roadType").asString()).isNotBlank();
            assertThat(e.get("delayFactor").asDouble()).isEqualTo(1.0);
            km += e.get("distanceKm").asDouble();
            min += e.get("timeMin").asDouble();
        }
        assertThat(route.get("totalDistanceKm").asDouble()).isCloseTo(km, within(1e-6));
        assertThat(route.get("totalTimeMin").asDouble()).isCloseTo(min, within(1e-6));

        // Geometria [lat, lon]: zaczyna się przy Warszawie, kończy przy Gdańsku, punktów więcej niż węzłów
        JsonNode line = route.get("geometryLatLon");
        assertThat(line.size()).isGreaterThan(nodes.size());
        RoadNode warszawa = node("Warszawa");
        RoadNode gdansk = node("Gdańsk");
        assertThat(line.get(0).get(0).asDouble()).isCloseTo(warszawa.getLat(), within(0.1));
        assertThat(line.get(0).get(1).asDouble()).isCloseTo(warszawa.getLon(), within(0.1));
        assertThat(line.get(line.size() - 1).get(0).asDouble()).isCloseTo(gdansk.getLat(), within(0.1));
        assertThat(line.get(line.size() - 1).get(1).asDouble()).isCloseTo(gdansk.getLon(), within(0.1));
    }

    @Test
    void dijkstraAndAStarGiveSameTimeAStarSettlesFewerNodes() throws Exception {
        JsonNode dijkstra = route("Szczecin", "Rzeszów", HIGH_VEHICLE, "DIJKSTRA");
        JsonNode aStar = route("Szczecin", "Rzeszów", HIGH_VEHICLE, "ASTAR");

        assertThat(aStar.get("totalTimeMin").asDouble())
                .isCloseTo(dijkstra.get("totalTimeMin").asDouble(), within(1e-6));
        assertThat(aStar.get("settledNodeCount").asInt()).isLessThan(dijkstra.get("settledNodeCount").asInt());
    }

    @Test
    void highVehicleDetoursAroundViaduct() throws Exception {
        JsonNode low = route("Bydgoszcz", "Piła", LOW_VEHICLE, "ASTAR");
        JsonNode high = route("Bydgoszcz", "Piła", HIGH_VEHICLE, "ASTAR");

        assertThat(nodeNames(low)).containsExactly("Bydgoszcz", "Piła");
        assertThat(nodeNames(high)).doesNotContainSequence("Bydgoszcz", "Piła")
                .startsWith("Bydgoszcz").endsWith("Piła");
        assertThat(high.get("totalTimeMin").asDouble()).isGreaterThan(low.get("totalTimeMin").asDouble());
    }

    @Test
    void noRouteForHighVehicleIs409() throws Exception {
        // Wszystkie drogi do Piły dostają wiadukt 3,8 m — pojazd 4,0 m nie ma jak dojechać
        long pilaId = node("Piła").getId();
        roadEdgeRepository.findAll().stream()
                .filter(e -> e.getToNode().getId().equals(pilaId))
                .forEach(e -> e.setMaxHeightM(3.8));
        roadEdgeRepository.flush();
        roadGraphService.reload();

        mockMvc.perform(get("/api/routes")
                        .param("fromNodeId", String.valueOf(node("Poznań").getId()))
                        .param("toNodeId", String.valueOf(pilaId))
                        .param("vehicleId", String.valueOf(vehicleId(HIGH_VEHICLE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule violation"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Piła")));

        // Niski pojazd nadal dojeżdża
        route("Poznań", "Piła", LOW_VEHICLE, "ASTAR");
    }

    @Test
    void unknownNodeOrVehicleIs404() throws Exception {
        String warszawa = String.valueOf(node("Warszawa").getId());
        String vehicle = String.valueOf(vehicleId(HIGH_VEHICLE));

        mockMvc.perform(get("/api/routes").param("fromNodeId", "999999").param("toNodeId", warszawa)
                        .param("vehicleId", vehicle))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/routes").param("fromNodeId", warszawa).param("toNodeId", warszawa)
                        .param("vehicleId", "999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingOrInvalidParameterIs400() throws Exception {
        String warszawa = String.valueOf(node("Warszawa").getId());

        mockMvc.perform(get("/api/routes").param("fromNodeId", warszawa).param("toNodeId", warszawa))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Required parameter 'vehicleId' is missing"));
        mockMvc.perform(get("/api/routes").param("fromNodeId", warszawa).param("toNodeId", warszawa)
                        .param("vehicleId", "1").param("algorithm", "BFS"))
                .andExpect(status().isBadRequest());
    }
}
