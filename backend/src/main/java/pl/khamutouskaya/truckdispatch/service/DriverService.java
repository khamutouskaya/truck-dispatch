package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.dto.DriverLocationRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverResponse;
import pl.khamutouskaya.truckdispatch.dto.DriverStatusRequest;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverService {

    private static final Sort BY_NAME = Sort.by("lastName", "firstName");
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final TransportOrderRepository transportOrderRepository;

    // status == null → wszyscy kierowcy
    public List<DriverResponse> findAll(DriverStatus status) {
        List<Driver> drivers = status == null
                ? driverRepository.findAll(BY_NAME)
                : driverRepository.findByStatus(status, BY_NAME);
        return drivers.stream()
                .map(DriverResponse::from)
                .toList();
    }

    public DriverResponse findById(Long id) {
        return DriverResponse.from(getDriver(id));
    }

    // Nowy kierowca: AVAILABLE, położenie nieznane, liczniki = 0 (wartości domyślne w encji)
    @Transactional
    public DriverResponse create(DriverRequest request) {
        Driver driver = new Driver();
        applyRequest(driver, request);
        return DriverResponse.from(driverRepository.save(driver));
    }

    @Transactional
    public DriverResponse update(Long id, DriverRequest request) {
        Driver driver = getDriver(id);
        applyRequest(driver, request);
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse updateLocation(Long id, DriverLocationRequest request) {
        Driver driver = getDriver(id);
        driver.setCurrentLat(request.lat());
        driver.setCurrentLon(request.lon());
        driver.setLocationSource(LocationSource.MANUAL);
        driver.setLocationUpdatedAt(LocalDateTime.now());
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse updateStatus(Long id, DriverStatusRequest request) {
        Driver driver = getDriver(id);
        driver.setStatus(request.status());
        return DriverResponse.from(driver);
    }

    @Transactional
    public void delete(Long id) {
        Driver driver = getDriver(id);

        if (transportOrderRepository.existsByAssignedDriverIdAndStatusIn(id, ACTIVE_ORDER_STATUSES)) {
            throw new ConflictException("Driver with id " + id + " has active orders (ASSIGNED or IN_PROGRESS)");
        }
        // Zrealizowane i anulowane zlecenia odwołują się do kierowcy (klucz obcy) i przechowują historię,
        // dlatego takiego kierowcy nie usuwamy — można go przenieść do OFF_DUTY
        if (transportOrderRepository.existsByAssignedDriverId(id)) {
            throw new ConflictException("Driver with id " + id
                    + " has order history and cannot be deleted; set status OFF_DUTY instead");
        }

        // Pojazd zostaje we flocie, ale bez kierowcy
        vehicleRepository.findByDriverId(id).ifPresent(vehicle -> vehicle.setDriver(null));
        driverRepository.delete(driver);
    }

    private Driver getDriver(Long id) {
        return driverRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Driver", id));
    }

    private void applyRequest(Driver driver, DriverRequest request) {
        driver.setFirstName(request.firstName());
        driver.setLastName(request.lastName());
        driver.setPhone(request.phone());
    }
}
