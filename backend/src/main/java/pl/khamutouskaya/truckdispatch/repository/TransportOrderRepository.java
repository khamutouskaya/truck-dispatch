package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

public interface TransportOrderRepository extends JpaRepository<TransportOrder, Long> {
}
