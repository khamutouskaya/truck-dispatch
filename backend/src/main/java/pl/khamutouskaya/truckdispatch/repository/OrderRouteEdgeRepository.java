package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;

import java.util.List;

public interface OrderRouteEdgeRepository extends JpaRepository<OrderRouteEdge, Long> {

    // Trasa zlecenia w kolejności jazdy; krawędź z węzłami jednym zapytaniem (współrzędne, geometria)
    @EntityGraph(attributePaths = {"edge", "edge.fromNode", "edge.toNode"})
    List<OrderRouteEdge> findByOrderIdOrderBySeq(Long orderId);
}
