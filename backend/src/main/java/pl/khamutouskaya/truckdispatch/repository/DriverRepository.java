package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    // Spring Data строит запрос по имени метода: WHERE status = ?
    List<Driver> findByStatus(DriverStatus status);
}
