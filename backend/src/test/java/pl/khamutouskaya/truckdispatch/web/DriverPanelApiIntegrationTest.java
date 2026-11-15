package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Panel kierowcy /api/me: id kierowcy z tokenu. Plus zakaz ręcznej zmiany położenia w trakcie zlecenia
 * (kierowca i dyspozytor). Jan Kowalski (id 1), Piotr Nowak (id 2).
 * W danych V3 nikt nie ma zleceń — test tworzy zlecenie jako dyspozytor (automatycznie trafia do Jana).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DriverPanelApiIntegrationTest {

    private static final long JAN_ID = 1;
    private static final long PIOTR_ID = 2;

    @Autowired private MockMvc mockMvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private DriverLocationRepository driverLocationRepository;

    private long nodeId(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name))
                .map(RoadNode::getId).findFirst().orElseThrow();
    }

    // Warszawa → Gdańsk, TILT: algorytm przydziela Jana (test AssignmentApiIntegrationTest)
    private long createOrderAssignedToJan() throws Exception {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String body = """
                {"loadingNodeId": %d, "unloadingNodeId": %d, "cargoWeightKg": 7000, "cargoVolumeM3": 30,
                 "requiredVehicleType": "TILT", "loadingDeadline": "%s", "deliveryDeadline": "%s",
                 "estimatedLoadingMin": 60, "estimatedUnloadingMin": 60}
                """.formatted(nodeId("Warszawa"), nodeId("Gdańsk"), now.plusHours(6), now.plusHours(30));
        String json = mockMvc.perform(post("/api/orders").with(TestAuth.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assignedDriverId").value(JAN_ID))
                .andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("id").asLong();
    }

    private static String location(double lat, double lon) {
        return "{\"lat\": %s, \"lon\": %s}".formatted(lat, lon);
    }

    @Test
    void profileShowsOwnDataAndVehicle() throws Exception {
        mockMvc.perform(get("/api/me").with(TestAuth.driver(JAN_ID, "jan.kowalski")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.id").value(JAN_ID))
                .andExpect(jsonPath("$.driver.lastName").value("Kowalski"))
                .andExpect(jsonPath("$.vehicle.registrationNumber").value("WX 12345"));

        // Ten sam adres, inny token — inne dane: id jest brane z tokenu
        mockMvc.perform(get("/api/me").with(TestAuth.driver(PIOTR_ID, "piotr.nowak")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.id").value(PIOTR_ID))
                .andExpect(jsonPath("$.vehicle.registrationNumber").value("KR 23456"));
    }

    @Test
    void driverSeesOnlyOwnOrders() throws Exception {
        long orderId = createOrderAssignedToJan();

        mockMvc.perform(get("/api/me/orders").with(TestAuth.driver(JAN_ID, "jan.kowalski")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(orderId))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        mockMvc.perform(get("/api/me/orders").with(TestAuth.driver(PIOTR_ID, "piotr.nowak")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void driverWithoutActiveOrderSetsLocationAsManualWithHistory() throws Exception {
        mockMvc.perform(patch("/api/me/location").with(TestAuth.driver(PIOTR_ID, "piotr.nowak"))
                        .contentType(MediaType.APPLICATION_JSON).content(location(50.2649, 19.0238)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PIOTR_ID))
                .andExpect(jsonPath("$.currentLat").value(50.2649))
                .andExpect(jsonPath("$.currentLon").value(19.0238))
                .andExpect(jsonPath("$.locationSource").value("MANUAL"));

        // Punkt trafił do historii położeń, bez zlecenia
        List<DriverLocation> history = driverLocationRepository.findAll().stream()
                .filter(l -> l.getDriver().getId() == PIOTR_ID).toList();
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getSource()).isEqualTo(LocationSource.MANUAL);
        assertThat(history.getFirst().getOrder()).isNull();
    }

    @Test
    void driverWithActiveOrderCannotSetLocation() throws Exception {
        createOrderAssignedToJan();

        mockMvc.perform(patch("/api/me/location").with(TestAuth.driver(JAN_ID, "jan.kowalski"))
                        .contentType(MediaType.APPLICATION_JSON).content(location(52.0, 20.0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule violation"));

        // Położenie się nie zmieniło (nadal Warszawa z V3)
        mockMvc.perform(get("/api/me").with(TestAuth.driver(JAN_ID, "jan.kowalski")))
                .andExpect(jsonPath("$.driver.currentLat").value(52.2297));
    }

    @Test
    void dispatcherCannotMoveDriverDuringActiveOrderEither() throws Exception {
        createOrderAssignedToJan();

        // Ta sama zasada dla dyspozytora: PATCH /api/drivers/{id}/location
        mockMvc.perform(patch("/api/drivers/{id}/location", JAN_ID).with(TestAuth.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(location(52.0, 20.0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business rule violation"));

        // Kierowca bez zlecenia — dyspozytor może zmienić położenie
        mockMvc.perform(patch("/api/drivers/{id}/location", PIOTR_ID).with(TestAuth.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(location(50.2649, 19.0238)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationSource").value("MANUAL"));
    }

    @Test
    void invalidCoordinatesGive400() throws Exception {
        mockMvc.perform(patch("/api/me/location").with(TestAuth.driver(PIOTR_ID, "piotr.nowak"))
                        .contentType(MediaType.APPLICATION_JSON).content(location(91, 19)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("lat"));
    }

    @Test
    void dispatcherHasNoDriverPanel() throws Exception {
        mockMvc.perform(get("/api/me/orders").with(TestAuth.dispatcher()))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/me/location").with(TestAuth.dispatcher())
                        .contentType(MediaType.APPLICATION_JSON).content(location(52.0, 20.0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void driverTokenWithoutDriverIdIsForbidden() throws Exception {
        // Rola DRIVER, ale bez claim driverId — nie wiadomo, czyje to dane
        mockMvc.perform(get("/api/me").with(jwt()
                        .jwt(t -> t.subject("x").claim("role", "DRIVER"))
                        .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"))))
                .andExpect(status().isForbidden());
    }
}
