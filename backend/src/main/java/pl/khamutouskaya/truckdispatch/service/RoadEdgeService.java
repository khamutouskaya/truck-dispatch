package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.dto.RoadEdgeResponse;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;

import java.util.Comparator;
import java.util.List;

/**
 * Krawędzie grafu z geometrią — do narysowania sieci dróg na mapie (warstwa z delayFactor).
 * Czyta z bazy, bo geometria nie jest trzymana w grafie w pamięci; delayFactor w bazie = w grafie
 * (samouczenie zapisuje i od razu przeładowuje graf).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadEdgeService {

    private final RoadEdgeRepository roadEdgeRepository;
    private final EdgeGeometryReader edgeGeometryReader;

    public List<RoadEdgeResponse> findAll() {
        return roadEdgeRepository.findAll().stream()   // węzły from/to pobierane razem (@EntityGraph)
                .sorted(Comparator.comparing(RoadEdge::getId))
                .map(this::toResponse)
                .toList();
    }

    private RoadEdgeResponse toResponse(RoadEdge edge) {
        List<double[]> line = edgeGeometryReader.read(edge).stream()
                .map(p -> new double[]{p.lat(), p.lon()})
                .toList();
        return new RoadEdgeResponse(edge.getId(),
                edge.getFromNode().getId(), edge.getFromNode().getName(),
                edge.getToNode().getId(), edge.getToNode().getName(),
                edge.getDistanceKm(), edge.getRoadType(), edge.getDelayFactor(), line);
    }
}
