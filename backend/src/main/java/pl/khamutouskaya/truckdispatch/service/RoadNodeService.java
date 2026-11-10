package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.dto.RoadNodeResponse;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;

import java.util.List;

// Punkty są tylko odczytywane: dodają je migracje Flyway
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoadNodeService {

    private final RoadNodeRepository roadNodeRepository;

    public List<RoadNodeResponse> findAll() {
        return roadNodeRepository.findAll(Sort.by("name")).stream()
                .map(RoadNodeResponse::from)
                .toList();
    }

    public RoadNodeResponse findById(Long id) {
        return roadNodeRepository.findById(id)
                .map(RoadNodeResponse::from)
                .orElseThrow(() -> new NotFoundException("Road node", id));
    }
}
