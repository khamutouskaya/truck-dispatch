package pl.khamutouskaya.truckdispatch.dto;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.RejectionReason;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.RejectedDriver;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentDecisionJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void decisionSavedWithOneReasonIsReadWithReasonsList() {
        String oldJson = """
                {"driverId": 3, "driverName": "Krzysztof Wiśniewski", "vehicleRegistration": "EL 34567",
                 "reason": "TYPE"}
                """;

        RejectedDriver driver = jsonMapper.readValue(oldJson, RejectedDriver.class);

        assertThat(driver.reason()).isEqualTo(RejectionReason.TYPE);
        assertThat(driver.reasons()).containsExactly(RejectionReason.TYPE);
    }

    @Test
    void allReasonsSurviveWritingAndReading() {
        RejectedDriver driver = new RejectedDriver(3, "Krzysztof Wiśniewski", "EL 34567",
                List.of(RejectionReason.TYPE, RejectionReason.LATE_LOADING));

        RejectedDriver read = jsonMapper.readValue(jsonMapper.writeValueAsString(driver), RejectedDriver.class);

        assertThat(read.reason()).isEqualTo(RejectionReason.TYPE);
        assertThat(read.reasons()).containsExactly(RejectionReason.TYPE, RejectionReason.LATE_LOADING);
    }
}
