package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

public record VehicleResponse(
        Long id,
        String registrationNumber,
        VehicleType type,
        int capacityKg,
        double volumeM3,
        Long driverId,
        String driverName
) {
    // Wywoływać wewnątrz transakcji: driver to relacja LAZY
    public static VehicleResponse from(Vehicle vehicle) {
        Driver driver = vehicle.getDriver();
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getRegistrationNumber(),
                vehicle.getType(),
                vehicle.getCapacityKg(),
                vehicle.getVolumeM3(),
                driver == null ? null : driver.getId(),
                driver == null ? null : driver.getFirstName() + " " + driver.getLastName()
        );
    }
}
