package pl.khamutouskaya.truckdispatch.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;

import java.util.List;

public interface RoadEdgeRepository extends JpaRepository<RoadEdge, Long> {

    // Cały graf jednym zapytaniem: węzły początkowy i końcowy pobierane przez JOIN (bez N+1)
    @Override
    @EntityGraph(attributePaths = {"fromNode", "toNode"})
    List<RoadEdge> findAll();
}
