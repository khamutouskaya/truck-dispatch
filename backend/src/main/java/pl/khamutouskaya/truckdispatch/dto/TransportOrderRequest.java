package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

import java.time.LocalDateTime;

// Здесь только проверки формы. Бизнес-правила (сроки, пункты, вместимость) — в TransportOrderService
public record TransportOrderRequest(
        @NotNull(message = "Loading node is required")
        Long loadingNodeId,

        @NotNull(message = "Unloading node is required")
        Long unloadingNodeId,

        @NotNull(message = "Cargo weight is required")
        @Positive(message = "Cargo weight must be positive")
        Integer cargoWeightKg,

        @NotNull(message = "Cargo volume is required")
        @Positive(message = "Cargo volume must be positive")
        Double cargoVolumeM3,

        @NotNull(message = "Required vehicle type is required")
        VehicleType requiredVehicleType,

        @NotNull(message = "Loading deadline is required")
        LocalDateTime loadingDeadline,

        @NotNull(message = "Delivery deadline is required")
        LocalDateTime deliveryDeadline,

        // Необязательные: если null, берётся default_handling_min пункта
        @PositiveOrZero(message = "Estimated loading time must not be negative")
        Integer estimatedLoadingMin,

        @PositiveOrZero(message = "Estimated unloading time must not be negative")
        Integer estimatedUnloadingMin
) {
}
