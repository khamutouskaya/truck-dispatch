package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;

import java.time.LocalDateTime;

// currentLat, currentLon, locationUpdatedAt, locationSource mogą być null (np. OFF_DUTY)
public record DriverResponse(
        Long id,
        String firstName,
        String lastName,
        String phone,
        DriverStatus status,
        Double currentLat,
        Double currentLon,
        LocalDateTime locationUpdatedAt,
        LocationSource locationSource,
        int drivingTimeTodayMin,
        int drivingTimeWeekMin,
        int continuousDrivingMin
) {
    public static DriverResponse from(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getFirstName(),
                driver.getLastName(),
                driver.getPhone(),
                driver.getStatus(),
                driver.getCurrentLat(),
                driver.getCurrentLon(),
                driver.getLocationUpdatedAt(),
                driver.getLocationSource(),
                driver.getDrivingTimeTodayMin(),
                driver.getDrivingTimeWeekMin(),
                driver.getContinuousDrivingMin()
        );
    }
}
