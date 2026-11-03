package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotNull;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;

public record DriverStatusRequest(
        @NotNull(message = "Status is required")
        DriverStatus status
) {
}
