package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;

public interface RoadNodeRepository extends JpaRepository<RoadNode, Long> {
}
