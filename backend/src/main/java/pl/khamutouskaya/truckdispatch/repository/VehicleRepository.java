package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Водитель подгружается тем же запросом (JOIN), чтобы не было N+1 запросов
    @Override
    @EntityGraph(attributePaths = "driver")
    List<Vehicle> findAll(Sort sort);

    boolean existsByRegistrationNumber(String registrationNumber);

    // Для PUT: такой номер есть у другой машины, не у этой
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long id);

    Optional<Vehicle> findByDriverId(Long driverId);

    // Есть ли хотя бы одна машина нужного типа, способная увезти груз
    boolean existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
            VehicleType type, int capacityKg, double volumeM3);
}
