package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Только личные данные. Статус и положение меняются отдельными PATCH,
// а счётчики времени вождения считает система
public record DriverRequest(
        @NotBlank(message = "First name is required")
        @Size(max = 50, message = "First name must be at most 50 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 50, message = "Last name must be at most 50 characters")
        String lastName,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone
) {
}
