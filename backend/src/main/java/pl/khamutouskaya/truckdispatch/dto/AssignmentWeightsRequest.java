package pl.khamutouskaya.truckdispatch.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Nowe wagi K1–K5 ze strony „Wagi”. Każda 0..1, suma = 1 (jak w CandidateScorer.validateWeights).
 */
public record AssignmentWeightsRequest(
        @NotNull @DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
        Double distanceToLoading,

        @NotNull @DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
        Double timeToLoading,

        @NotNull @DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
        Double remainingDrivingToday,

        @NotNull @DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
        Double stopCount,

        @NotNull @DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
        Double vehicleUtilization
) {

    // Tolerancja na błędy zaokrągleń (0.1 + 0.2 ≠ 0.3 w double)
    private static final double SUM_TOLERANCE = 1e-6;

    // Walidacja całego obiektu: błąd trafia do errors[] jako pole "sumOne"
    @AssertTrue(message = "Weights must sum to 1")
    public boolean isSumOne() {
        if (distanceToLoading == null || timeToLoading == null || remainingDrivingToday == null
                || stopCount == null || vehicleUtilization == null) {
            return true; // brak wartości zgłasza już @NotNull
        }
        double sum = distanceToLoading + timeToLoading + remainingDrivingToday + stopCount + vehicleUtilization;
        return Math.abs(sum - 1.0) <= SUM_TOLERANCE;
    }
}
