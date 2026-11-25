package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.AssignmentWeights;

import java.util.Optional;

public interface AssignmentWeightsRepository extends JpaRepository<AssignmentWeights, Long> {

    // Obowiązujące wagi = ostatnio zapisane (id rośnie z każdym zapisem)
    Optional<AssignmentWeights> findFirstByOrderByIdDesc();
}
