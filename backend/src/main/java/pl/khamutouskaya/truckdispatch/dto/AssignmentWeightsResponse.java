package pl.khamutouskaya.truckdispatch.dto;

import java.time.LocalDateTime;

/**
 * Obowiązujące wagi i wagi domyślne (do przycisku „Przywróć domyślne” i porównania na stronie).
 *
 * @param current   wagi używane teraz przez algorytm przydziału
 * @param defaults  wagi z application.yaml
 * @param isDefault true, gdy dyspozytor nic nie zapisał albo przywrócił domyślne
 * @param updatedAt chwila ostatniego zapisu; null przy wagach domyślnych
 */
public record AssignmentWeightsResponse(
        Weights current,
        Weights defaults,
        boolean isDefault,
        LocalDateTime updatedAt
) {

    // K1–K5 w kolejności kolumn rankingu
    public record Weights(
            double distanceToLoading,
            double timeToLoading,
            double remainingDrivingToday,
            double stopCount,
            double vehicleUtilization
    ) {
    }
}
