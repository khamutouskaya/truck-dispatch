package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;

/**
 * Kandydat, który przeszedł odsiew i dla którego policzono trasy i symulację czasu pracy.
 * Zawiera wszystko, czego potrzebują kryteria K1–K5.
 *
 * @param driverId            id kierowcy
 * @param driverName          imię i nazwisko (do wyjaśnienia decyzji)
 * @param vehicleCapacityKg   ładowność pojazdu kierowcy [kg] (K5)
 * @param distanceToLoadingKm długość dojazdu do załadunku [km] (K1)
 * @param simulation          symulacja zlecenia od chwili „teraz” (K2, K3, K4)
 */
public record Candidate(
        long driverId,
        String driverName,
        int vehicleCapacityKg,
        double distanceToLoadingKm,
        WorkTimeSimulation simulation
) {
    public Candidate {
        if (vehicleCapacityKg <= 0) {
            throw new IllegalArgumentException("Vehicle capacity must be positive");
        }
        if (distanceToLoadingKm < 0) {
            throw new IllegalArgumentException("Distance must be non-negative");
        }
    }
}
