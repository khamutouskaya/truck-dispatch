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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pełna ścieżka HTTP na prawdziwej bazie: POST /api/orders z automatycznym przydziałem i nowe endpointy.
 * @Transactional — wszystko, co zapisze test (także przez HTTP w MockMvc), jest wycofywane.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssignmentApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;

    private long nodeId(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name))
                .map(RoadNode::getId).findFirst().orElseThrow();
    }

    // Tworzy zlecenie przez API i zwraca odpowiedź (TransportOrderResponse)
    private JsonNode createOrder(String from, String to, int loadingInMinutes) throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String body = """
                {"loadingNodeId": %d, "unloadingNodeId": %d, "cargoWeightKg": 7000, "cargoVolumeM3": 30,
                 "requiredVehicleType": "TILT", "loadingDeadline": "%s", "deliveryDeadline": "%s",
                 "estimatedLoadingMin": 60, "estimatedUnloadingMin": 60}
                """.formatted(nodeId(from), nodeId(to), now.plusMinutes(loadingInMinutes), now.plusHours(30));
        String json = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json);
    }

    @Test
    void createdOrderIsAssignedAutomaticallyAndDecisionIsReadable() throws Exception {
        JsonNode order = createOrder("Warszawa", "Gdańsk", 6 * 60);

        // POST /api/orders: wynik algorytmu widać od razu w statusie
        assertThat(order.get("status").asString()).isEqualTo("ASSIGNED");
        assertThat(order.get("assignedDriverName").asString()).isEqualTo("Jan Kowalski");
        long id = order.get("id").asLong();

        // Ekran „Szczegóły zlecenia”: ranking z powodami
        mockMvc.perform(get("/api/orders/{id}/assignment", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDriverId").value(1))
                .andExpect(jsonPath("$.criteria.length()").value(5))
                .andExpect(jsonPath("$.ranking[0].rank").value(1))
                .andExpect(jsonPath("$.ranking[0].values.distanceToLoadingKm.normalized").exists())
                .andExpect(jsonPath("$.rejected[?(@.driverId == 6)].reason").value("LATE_LOADING"));

        // Zlecenie już przydzielone — podgląd i ponowny przydział są zabronione
        mockMvc.perform(get("/api/orders/{id}/candidates", id)).andExpect(status().isConflict());
        mockMvc.perform(post("/api/orders/{id}/assign", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule violation"));
    }

    @Test
    void orderNobodyCanTakeStaysNewAndCanBeRetried() throws Exception {
        // Załadunek w Krakowie za 30 min — żaden kierowca z plandeką nie zdąży
        JsonNode order = createOrder("Kraków", "Gdańsk", 30);
        long id = order.get("id").asLong();

        assertThat(order.get("status").asString()).isEqualTo("NEW");
        assertThat(order.get("assignedDriverId").isNull()).isTrue();

        // Powody odrzucenia są zapisane
        mockMvc.perform(get("/api/orders/{id}/assignment", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedDriverId").isEmpty())
                .andExpect(jsonPath("$.ranking").isEmpty())
                .andExpect(jsonPath("$.rejected[?(@.driverId == 1)].reason").value("LATE_LOADING"));

        // Zlecenie NEW: można podejrzeć kandydatów i uruchomić algorytm ponownie (np. po zmianie danych)
        mockMvc.perform(get("/api/orders/{id}/candidates", id)).andExpect(status().isOk());
        mockMvc.perform(post("/api/orders/{id}/assign", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(id));
    }

    @Test
    void unknownOrderGives404() throws Exception {
        mockMvc.perform(get("/api/orders/{id}/assignment", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders/{id}/candidates", 999_999)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/{id}/assign", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void orderFromTestDataWithoutDecisionGives404ForAssignment() throws Exception {
        // Zlecenia z V3 powstały migracją, algorytm dla nich jeszcze nie działał
        mockMvc.perform(get("/api/orders/{id}/assignment", 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No assignment decision for order 1"));
    }
}
