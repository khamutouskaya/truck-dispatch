package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.dto.DriverLocationRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverResponse;
import pl.khamutouskaya.truckdispatch.dto.DriverStatusRequest;
import pl.khamutouskaya.truckdispatch.dto.MyProfileResponse;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.dto.VehicleResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.ErrorCode;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DriverService {

    private static final Sort BY_NAME = Sort.by("lastName", "firstName");
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);

    private final DriverRepository driverRepository;
    private final DriverLocationRepository driverLocationRepository;
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

    /**
     * Ręczna zmiana położenia (źródło MANUAL) — przez dyspozytora albo samego kierowcę.
     * Zabroniona w trakcie zlecenia (ASSIGNED / IN_PROGRESS): wtedy położenie zapisuje symulator,
     * a ręczna zmiana zepsułaby ślad GPS, z którego samouczenie liczy rzeczywiste czasy przejazdu krawędzi.
     */
    @Transactional
    public DriverResponse updateLocation(Long id, DriverLocationRequest request) {
        Driver driver = getDriver(id);
        if (transportOrderRepository.existsByAssignedDriverIdAndStatusIn(id, ACTIVE_ORDER_STATUSES)) {
            throw new ConflictException(ErrorCode.LOCATION_LOCKED_DURING_ORDER,
                    "Location cannot be changed during an active order (ASSIGNED or IN_PROGRESS): "
                            + "it is recorded by the simulator and used to learn travel times",
                    Map.of("driverName", driver.getFullName()));
        }
        recordManualLocation(driver, request);
        return DriverResponse.from(driver);
    }

    // --- Panel kierowcy (/api/me): id kierowcy pochodzi z tokenu, nie z adresu ---

    // Własny profil: dane kierowcy i jego pojazd (może go nie mieć)
    public MyProfileResponse findMyProfile(Long driverId) {
        Driver driver = getDriver(driverId);
        VehicleResponse vehicle = vehicleRepository.findByDriverId(driverId).map(VehicleResponse::from).orElse(null);
        return new MyProfileResponse(DriverResponse.from(driver), vehicle);
    }

    public List<TransportOrderResponse> findMyOrders(Long driverId) {
        getDriver(driverId);
        // Wszystkie zlecenia są tego kierowcy — jego pojazd wystarczy odczytać raz
        String registration = vehicleRepository.findByDriverId(driverId)
                .map(Vehicle::getRegistrationNumber)
                .orElse(null);
        return transportOrderRepository.findByAssignedDriverId(driverId, NEWEST_FIRST).stream()
                .map(order -> TransportOrderResponse.from(order, registration))
                .toList();
    }

    // Kierowca sam ustawia swoje położenie — te same zasady co dla dyspozytora (updateLocation)
    @Transactional
    public DriverResponse updateMyLocation(Long driverId, DriverLocationRequest request) {
        return updateLocation(driverId, request);
    }

    // Nowe bieżące położenie + punkt w historii (driver_location)
    private void recordManualLocation(Driver driver, DriverLocationRequest request) {
        driver.setCurrentLat(request.lat());
        driver.setCurrentLon(request.lon());
        driver.setLocationSource(LocationSource.MANUAL);
        driver.setLocationUpdatedAt(LocalDateTime.now());

        DriverLocation location = new DriverLocation();
        location.setDriver(driver);
        location.setLat(request.lat());
        location.setLon(request.lon());
        location.setRecordedAt(driver.getLocationUpdatedAt());
        location.setSource(LocationSource.MANUAL);
        driverLocationRepository.save(location);
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
            throw new ConflictException(ErrorCode.DRIVER_HAS_ACTIVE_ORDERS,
                    "Driver with id " + id + " has active orders (ASSIGNED or IN_PROGRESS)",
                    Map.of("driverName", driver.getFullName()));
        }
        // Zrealizowane i anulowane zlecenia odwołują się do kierowcy (klucz obcy) i przechowują historię,
        // dlatego takiego kierowcy nie usuwamy — można go przenieść do OFF_DUTY
        if (transportOrderRepository.existsByAssignedDriverId(id)) {
            throw new ConflictException(ErrorCode.DRIVER_HAS_ORDER_HISTORY,
                    "Driver with id " + id + " has order history and cannot be deleted; set status OFF_DUTY instead",
                    Map.of("driverName", driver.getFullName()));
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
