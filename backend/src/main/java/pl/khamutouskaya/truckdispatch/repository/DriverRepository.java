package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;

import java.util.List;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    // Spring Data buduje zapytanie na podstawie nazwy metody: WHERE status = ?
    List<Driver> findByStatus(DriverStatus status);

    List<Driver> findByStatus(DriverStatus status, Sort sort);
}
