package pl.khamutouskaya.truckdispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.CandidateScorer;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.DefaultCriteria;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.WeightedCriterion;

import java.util.List;

/**
 * Wagi kryteriów K1–K5 z application.yaml (assignment.weights.*).
 * Błędne wagi (suma ≠ 1, wartość ujemna) zatrzymują start aplikacji — lepiej od razu niż przy pierwszym zleceniu.
 */
@ConfigurationProperties(prefix = "assignment.weights")
public record AssignmentWeightsProperties(
        double distanceToLoading,      // K1
        double timeToLoading,          // K2
        double remainingDrivingToday,  // K3
        double stopCount,              // K4
        double vehicleUtilization      // K5
) {
    public AssignmentWeightsProperties {
        CandidateScorer.validateWeights(DefaultCriteria.withWeights(
                distanceToLoading, timeToLoading, remainingDrivingToday, stopCount, vehicleUtilization));
    }

    public List<WeightedCriterion> toCriteria() {
        return DefaultCriteria.withWeights(distanceToLoading, timeToLoading, remainingDrivingToday, stopCount,
                vehicleUtilization);
    }
}
