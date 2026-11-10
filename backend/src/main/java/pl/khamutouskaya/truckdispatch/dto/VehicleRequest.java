package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

// Typy opakowujące (Integer, Double), aby brakujące pole było null i zostało wychwycone przez @NotNull
public record VehicleRequest(
        @NotBlank(message = "Registration number is required")
        @Size(max = 15, message = "Registration number must be at most 15 characters")
        @Pattern(regexp = "^[A-Z]{2,3} [A-Z0-9]{4,5}$",
                message = "Registration number must look like 'WX 12345'")
        String registrationNumber,

        @NotNull(message = "Vehicle type is required")
        VehicleType type,

        @NotNull(message = "Capacity is required")
        @Positive(message = "Capacity must be positive")
        Integer capacityKg,

        @NotNull(message = "Volume is required")
        @Positive(message = "Volume must be positive")
        Double volumeM3,

        @NotNull(message = "Height is required")
        @Positive(message = "Height must be positive")
        Double heightM,

        @NotNull(message = "Empty weight is required")
        @Positive(message = "Empty weight must be positive")
        Integer emptyWeightKg,

        // Opcjonalne: pojazd może nie mieć kierowcy
        Long driverId
) {
}
