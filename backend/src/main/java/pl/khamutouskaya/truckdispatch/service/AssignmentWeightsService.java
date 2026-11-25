package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.DefaultCriteria;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.WeightedCriterion;
import pl.khamutouskaya.truckdispatch.config.AssignmentWeightsProperties;
import pl.khamutouskaya.truckdispatch.domain.AssignmentWeights;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsRequest;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsResponse;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsResponse.Weights;
import pl.khamutouskaya.truckdispatch.repository.AssignmentWeightsRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Wagi K1–K5 dla algorytmu przydziału: ostatnio zapisane przez dyspozytora albo domyślne z application.yaml.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentWeightsService {

    // Wagi z suwaków mają krok 0.05 — porównanie z tolerancją na zaokrąglenia double
    private static final double EQUALITY_TOLERANCE = 1e-9;

    private final AssignmentWeightsRepository assignmentWeightsRepository;
    private final AssignmentWeightsProperties defaults;
    private final Clock clock;

    // Dla AssignmentService: kryteria K1–K5 z obowiązującymi wagami
    public List<WeightedCriterion> currentCriteria() {
        Weights w = toResponse(assignmentWeightsRepository.findFirstByOrderByIdDesc()).current();
        return DefaultCriteria.withWeights(w.distanceToLoading(), w.timeToLoading(), w.remainingDrivingToday(),
                w.stopCount(), w.vehicleUtilization());
    }

    public AssignmentWeightsResponse current() {
        return toResponse(assignmentWeightsRepository.findFirstByOrderByIdDesc());
    }

    // Nowy wiersz zamiast nadpisywania — zostaje historia zmian wag
    @Transactional
    public AssignmentWeightsResponse update(AssignmentWeightsRequest request) {
        return save(new Weights(request.distanceToLoading(), request.timeToLoading(),
                request.remainingDrivingToday(), request.stopCount(), request.vehicleUtilization()));
    }

    // Przywrócenie wag z application.yaml — też jako nowy wiersz historii
    @Transactional
    public AssignmentWeightsResponse resetToDefaults() {
        return save(defaultWeights());
    }

    private AssignmentWeightsResponse save(Weights weights) {
        AssignmentWeights entity = new AssignmentWeights();
        entity.setDistanceToLoading(weights.distanceToLoading());
        entity.setTimeToLoading(weights.timeToLoading());
        entity.setRemainingDrivingToday(weights.remainingDrivingToday());
        entity.setStopCount(weights.stopCount());
        entity.setVehicleUtilization(weights.vehicleUtilization());
        entity.setCreatedAt(LocalDateTime.now(clock));
        return toResponse(Optional.of(assignmentWeightsRepository.save(entity)));
    }

    private AssignmentWeightsResponse toResponse(Optional<AssignmentWeights> saved) {
        Weights defaultWeights = defaultWeights();
        if (saved.isEmpty()) {
            return new AssignmentWeightsResponse(defaultWeights, defaultWeights, true, null);
        }
        AssignmentWeights entity = saved.get();
        Weights current = new Weights(entity.getDistanceToLoading(), entity.getTimeToLoading(),
                entity.getRemainingDrivingToday(), entity.getStopCount(), entity.getVehicleUtilization());
        return new AssignmentWeightsResponse(current, defaultWeights, sameWeights(current, defaultWeights),
                entity.getCreatedAt());
    }

    private Weights defaultWeights() {
        return new Weights(defaults.distanceToLoading(), defaults.timeToLoading(), defaults.remainingDrivingToday(),
                defaults.stopCount(), defaults.vehicleUtilization());
    }

    private static boolean sameWeights(Weights a, Weights b) {
        return close(a.distanceToLoading(), b.distanceToLoading())
                && close(a.timeToLoading(), b.timeToLoading())
                && close(a.remainingDrivingToday(), b.remainingDrivingToday())
                && close(a.stopCount(), b.stopCount())
                && close(a.vehicleUtilization(), b.vehicleUtilization());
    }

    private static boolean close(double a, double b) {
        return Math.abs(a - b) <= EQUALITY_TOLERANCE;
    }
}
