package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.learning.DelayFactorLearner;
import pl.khamutouskaya.truckdispatch.algorithm.learning.TrackPoint;
import pl.khamutouskaya.truckdispatch.algorithm.learning.TrackProjector;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Samouczenie po wykonanym zleceniu (docs/karta-i-algorytm.md, krok 11):
 * ślad GPS → rzeczywisty czas jazdy po każdej krawędzi (TrackProjector) →
 * delayFactor = 0,8 · stary + 0,2 · (fakt / czas bazowy) → zapis w road_edge → przeładowanie grafu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LearningService {

    private final PlannedRouteLoader plannedRouteLoader;
    private final DriverLocationRepository driverLocationRepository;
    private final RoadEdgeRepository roadEdgeRepository;
    private final RoadGraphService roadGraphService;

    /**
     * @return id krawędzi → nowy delayFactor (tylko krawędzie zmierzone w całości)
     */
    @Transactional
    public Map<Long, Double> learnFromOrder(Long orderId) {
        PlannedRouteLoader.PlannedRoute route = plannedRouteLoader.load(orderId);
        Map<Integer, Double> actualByEdge = TrackProjector.drivingTimeByEdge(route.all(), track(orderId));

        Map<Long, Double> updated = new LinkedHashMap<>();
        actualByEdge.forEach((seq, actualMin) -> {
            OrderRouteEdge routeEdge = route.bySeq().get(seq);
            RoadEdge edge = routeEdge.getEdge();
            double oldFactor = edge.getDelayFactor();
            double newFactor = DelayFactorLearner.update(oldFactor, actualMin, routeEdge.getBaseTimeMin());
            edge.setDelayFactor(newFactor);
            updated.put(edge.getId(), newFactor);
            log.debug("Learning: edge {} {} → {}: actual {} / base {} min, delayFactor {} → {}",
                    edge.getId(), edge.getFromNode().getName(), edge.getToNode().getName(),
                    round(actualMin), round(routeEdge.getBaseTimeMin()), round(oldFactor), round(newFactor));
        });

        if (!updated.isEmpty()) {
            // Najpierw zapis do bazy, potem graf w pamięci czyta nowe wartości — kolejne trasy już z nich korzystają
            roadEdgeRepository.flush();
            roadGraphService.reload();
        }
        log.info("Learning: order {} — delayFactor updated on {} of {} route edges",
                orderId, updated.size(), route.bySeq().size());
        return updated;
    }

    // Ślad GPS zlecenia; czas liczony od pierwszego punktu [min]
    private List<TrackPoint> track(Long orderId) {
        List<DriverLocation> locations = driverLocationRepository.findByOrderIdOrderByRecordedAtAscIdAsc(orderId);
        if (locations.isEmpty()) {
            return List.of();
        }
        LocalDateTime start = locations.getFirst().getRecordedAt();
        return locations.stream()
                .map(l -> new TrackPoint(new GeoPoint(l.getLat(), l.getLon()),
                        Duration.between(start, l.getRecordedAt()).toMillis() / 60_000.0))
                .toList();
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
