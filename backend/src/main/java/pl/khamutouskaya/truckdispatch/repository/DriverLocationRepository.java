package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;

import java.util.List;
import java.util.Optional;

public interface DriverLocationRepository extends JpaRepository<DriverLocation, Long> {

    // Ślad GPS zlecenia w kolejności czasu — wejście do rzutowania na trasę (samouczenie)
    List<DriverLocation> findByOrderIdOrderByRecordedAtAscIdAsc(Long orderId);

    // Pierwszy punkt zlecenia = start podróży (miejsce i chwila); ostatni = dokąd doszła symulacja
    Optional<DriverLocation> findFirstByOrderIdOrderByRecordedAtAscIdAsc(Long orderId);

    Optional<DriverLocation> findFirstByOrderIdOrderByRecordedAtDescIdDesc(Long orderId);
}
