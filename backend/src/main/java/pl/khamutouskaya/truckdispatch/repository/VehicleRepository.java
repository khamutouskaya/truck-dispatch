package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    // Kierowca jest pobierany tym samym zapytaniem (JOIN), aby uniknąć problemu N+1 zapytań
    @Override
    @EntityGraph(attributePaths = "driver")
    List<Vehicle> findAll(Sort sort);

    boolean existsByRegistrationNumber(String registrationNumber);

    // Dla PUT: taki numer ma inny pojazd, a nie ten
    boolean existsByRegistrationNumberAndIdNot(String registrationNumber, Long id);

    Optional<Vehicle> findByDriverId(Long driverId);

    // Czy istnieje choć jeden pojazd danego typu zdolny przewieźć ładunek
    boolean existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
            VehicleType type, int capacityKg, double volumeM3);
}
