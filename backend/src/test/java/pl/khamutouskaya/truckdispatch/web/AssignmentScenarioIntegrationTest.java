package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GeoDistance;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Scenariusze algorytmu przydziału na prawdziwym grafie i danych testowych (V3):
 * najbliższy kierowca NIE zawsze wygrywa — filtry twarde odrzucają go z konkretnym powodem.
 * <p>
 * Kierowcy z plandeką (TILT): Jan Kowalski (Warszawa, 24 t), Paweł Kamiński (Gdańsk, 12 t),
 * Michał Zieliński (Lublin, 8 t, w tym tygodniu już 3100 z 3360 min jazdy).
 * Załadunek w Lublinie — Michał stoi na miejscu, więc jest najbliższy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AssignmentScenarioIntegrationTest {

    private static final long MICHAL_ID = 8;
    private static final long JAN_ID = 1;

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private VehicleRepository vehicleRepository;

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    // Zlecenie TILT z terminami z zapasem: liczą się tylko pojemność i czas pracy, nie pośpiech
    private long createOrder(String from, String to, int cargoWeightKg) throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String body = """
                {"loadingNodeId": %d, "unloadingNodeId": %d, "cargoWeightKg": %d, "cargoVolumeM3": 30,
                 "requiredVehicleType": "TILT", "loadingDeadline": "%s", "deliveryDeadline": "%s",
                 "estimatedLoadingMin": 60, "estimatedUnloadingMin": 60}
                """.formatted(node(from).getId(), node(to).getId(), cargoWeightKg,
                now.plusHours(12), now.plusHours(48));
        String json = mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asLong();
    }

    private JsonNode decision(long orderId) throws Exception {
        String json = mockMvc.perform(get("/api/orders/{id}/assignment", orderId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json);
    }

    private static String reasonOf(JsonNode decision, long driverId) {
        for (JsonNode r : decision.get("rejected")) {
            if (r.get("driverId").asLong() == driverId) {
                return r.get("reason").asString();
            }
        }
        throw new AssertionError("Driver " + driverId + " is not rejected: " + decision);
    }

    private static JsonNode winner(JsonNode decision) {
        JsonNode first = decision.get("ranking").get(0);
        assertThat(first.get("assigned").asBoolean()).isTrue();
        return first;
    }

    // Warunek wstępny: Michał jest najbliższym dostępnym kierowcą TILT do Lublina
    private void assertMichalIsNearestTiltDriverTo(String loading) {
        RoadNode target = node(loading);
        List<Driver> tilt = driverRepository.findAll().stream()
                .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
                .filter(d -> vehicleRepository.findByDriverId(d.getId())
                        .map(v -> v.getType() == VehicleType.TILT).orElse(false))
                .sorted(Comparator.comparingDouble(d -> GeoDistance.haversineKm(
                        d.getCurrentLat(), d.getCurrentLon(), target.getLat(), target.getLon())))
                .toList();
        assertThat(tilt).extracting(Driver::getId).startsWith(MICHAL_ID);
    }

    @Test
    void nearestDriverWithTooSmallVehicleIsRejectedForCapacity() throws Exception {
        assertMichalIsNearestTiltDriverTo("Lublin");

        // 10 t ładunku: pojazd Michała ma 8 t ładowności
        long orderId = createOrder("Lublin", "Kraków", 10_000);

        JsonNode decision = decision(orderId);
        assertThat(reasonOf(decision, MICHAL_ID)).isEqualTo("CAPACITY");
        JsonNode winner = winner(decision);
        assertThat(winner.get("driverId").asLong()).isEqualTo(JAN_ID);
        assertThat(decision.get("assignedDriverId").asLong()).isEqualTo(JAN_ID);
        // Zwycięzca jedzie do załadunku z daleka (Warszawa → Lublin), a nie stoi na miejscu
        assertThat(winner.get("values").get("distanceToLoadingKm").get("raw").asDouble()).isGreaterThan(100);
    }

    @Test
    void nearestDriverWithoutWeeklyDrivingTimeIsRejected() throws Exception {
        assertMichalIsNearestTiltDriverTo("Lublin");
        Driver michal = driverRepository.findById(MICHAL_ID).orElseThrow();
        // 3360 − 3100 = 260 min jazdy do końca tygodnia (UE 561/2006: 56 h)
        assertThat(michal.getDrivingTimeWeekMin()).isEqualTo(3100);

        // 5 t — pojemność wystarcza; ale Lublin → Gdańsk to dużo więcej niż 260 min jazdy
        long orderId = createOrder("Lublin", "Gdańsk", 5_000);

        JsonNode decision = decision(orderId);
        assertThat(reasonOf(decision, MICHAL_ID)).isEqualTo("WEEKLY_LIMIT");
        JsonNode winner = winner(decision);
        assertThat(winner.get("driverId").asLong()).isEqualTo(JAN_ID);
        assertThat(winner.get("values").get("distanceToLoadingKm").get("raw").asDouble()).isGreaterThan(100);
    }
}
