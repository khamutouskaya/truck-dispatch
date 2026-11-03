package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

// Обёртки (Integer, Double), чтобы отсутствующее поле было null и его поймал @NotNull
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

        // Необязательно: машина может быть без водителя
        Long driverId
) {
}
