package pl.khamutouskaya.truckdispatch.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pl.khamutouskaya.truckdispatch.service.AssignmentWeightsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Strona „Wagi” przez HTTP na prawdziwej bazie. @Transactional — zapisane wagi są wycofywane po teście.
 */
@SpringBootTest
@Transactional
class AssignmentWeightsApiIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private AssignmentWeightsService assignmentWeightsService;

    private MockMvc asDispatcher() {
        return TestAuth.mockMvcAs(context, TestAuth.dispatcher());
    }

    @Test
    void savedWeightsAreReturnedAndUsedByTheAlgorithm() throws Exception {
        String body = """
                {"distanceToLoading": 0.1, "timeToLoading": 0.5, "remainingDrivingToday": 0.2,
                 "stopCount": 0.1, "vehicleUtilization": 0.1}
                """;
        asDispatcher().perform(put("/api/settings/weights").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.timeToLoading").value(0.5))
                .andExpect(jsonPath("$.defaults.timeToLoading").value(0.30))
                .andExpect(jsonPath("$.isDefault").value(false));

        asDispatcher().perform(get("/api/settings/weights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.remainingDrivingToday").value(0.2));

        // Algorytm bierze nowe wagi od następnego przydziału
        assertThat(assignmentWeightsService.currentCriteria().get(1).weight()).isEqualTo(0.5);
    }

    @Test
    void weightsThatDoNotSumToOneAreRejected() throws Exception {
        String body = """
                {"distanceToLoading": 0.25, "timeToLoading": 0.25, "remainingDrivingToday": 0.15,
                 "stopCount": 0.15, "vehicleUtilization": 0.15}
                """;
        asDispatcher().perform(put("/api/settings/weights").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sumOne"));
    }

    @Test
    void resetRestoresTheDefaults() throws Exception {
        String body = """
                {"distanceToLoading": 0.2, "timeToLoading": 0.2, "remainingDrivingToday": 0.2,
                 "stopCount": 0.2, "vehicleUtilization": 0.2}
                """;
        asDispatcher().perform(put("/api/settings/weights").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        asDispatcher().perform(post("/api/settings/weights/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.current.distanceToLoading").value(0.25));
    }

    @Test
    void driverCannotChangeWeights() throws Exception {
        TestAuth.mockMvcAs(context, TestAuth.driver(1L, "jan.kowalski"))
                .perform(get("/api/settings/weights"))
                .andExpect(status().isForbidden());
    }
}
