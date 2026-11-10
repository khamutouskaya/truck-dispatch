package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.dto.VehicleRequest;
import pl.khamutouskaya.truckdispatch.dto.VehicleResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;

    public List<VehicleResponse> findAll() {
        return vehicleRepository.findAll(Sort.by("registrationNumber")).stream()
                .map(VehicleResponse::from)
                .toList();
    }

    public VehicleResponse findById(Long id) {
        return VehicleResponse.from(getVehicle(id));
    }

    @Transactional
    public VehicleResponse create(VehicleRequest request) {
        if (vehicleRepository.existsByRegistrationNumber(request.registrationNumber())) {
            throw duplicateRegistration(request.registrationNumber());
        }
        Vehicle vehicle = new Vehicle();
        applyRequest(vehicle, request);
        return VehicleResponse.from(vehicleRepository.save(vehicle));
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleRequest request) {
        Vehicle vehicle = getVehicle(id);
        if (vehicleRepository.existsByRegistrationNumberAndIdNot(request.registrationNumber(), id)) {
            throw duplicateRegistration(request.registrationNumber());
        }
        applyRequest(vehicle, request);
        // save nie jest potrzebne: encja jest zarządzana (managed), Hibernate sam zapisze zmiany przy commicie
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public void delete(Long id) {
        vehicleRepository.delete(getVehicle(id));
    }

    private Vehicle getVehicle(Long id) {
        return vehicleRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Vehicle", id));
    }

    private void applyRequest(Vehicle vehicle, VehicleRequest request) {
        vehicle.setRegistrationNumber(request.registrationNumber());
        vehicle.setType(request.type());
        vehicle.setCapacityKg(request.capacityKg());
        vehicle.setVolumeM3(request.volumeM3());
        vehicle.setHeightM(request.heightM());
        vehicle.setEmptyWeightKg(request.emptyWeightKg());
        vehicle.setDriver(resolveDriver(vehicle, request.driverId()));
    }

    // Kierowca musi istnieć i nie może mieć innego pojazdu
    private Driver resolveDriver(Vehicle vehicle, Long driverId) {
        if (driverId == null) {
            return null;
        }
        Driver driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new NotFoundException("Driver", driverId));

        vehicleRepository.findByDriverId(driverId)
                .filter(other -> !other.getId().equals(vehicle.getId()))
                .ifPresent(other -> {
                    throw new ConflictException("Driver with id " + driverId
                            + " already has vehicle " + other.getRegistrationNumber());
                });
        return driver;
    }

    private ConflictException duplicateRegistration(String registrationNumber) {
        return new ConflictException("Vehicle with registration number " + registrationNumber + " already exists");
    }
}
