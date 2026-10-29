package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {
}
