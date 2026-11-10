package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Tylko dane osobowe. Status i położenie zmieniane są osobnymi żądaniami PATCH,
// a liczniki czasu jazdy oblicza system
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
