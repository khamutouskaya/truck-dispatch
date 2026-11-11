package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.AssignmentResult;

import java.util.List;

public interface AssignmentResultRepository extends JpaRepository<AssignmentResult, Long> {

    // Historia decyzji dla zlecenia, najnowsza pierwsza; kierowca od razu (nazwisko w DTO)
    @EntityGraph(attributePaths = {"driver"})
    List<AssignmentResult> findByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);
}
