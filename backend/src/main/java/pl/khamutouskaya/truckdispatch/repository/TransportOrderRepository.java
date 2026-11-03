package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

import java.util.Collection;
import java.util.List;

public interface TransportOrderRepository extends JpaRepository<TransportOrder, Long> {

    // Пункты и водитель подгружаются одним запросом — для списка в DTO
    @Override
    @EntityGraph(attributePaths = {"loadingNode", "unloadingNode", "assignedDriver"})
    List<TransportOrder> findAll(Sort sort);

    @EntityGraph(attributePaths = {"loadingNode", "unloadingNode", "assignedDriver"})
    List<TransportOrder> findByStatus(OrderStatus status, Sort sort);

    boolean existsByAssignedDriverIdAndStatusIn(Long driverId, Collection<OrderStatus> statuses);

    boolean existsByAssignedDriverId(Long driverId);
}
