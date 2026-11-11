package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion.DistanceToLoadingCriterion;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion.RemainingDrivingTodayCriterion;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion.StopCountCriterion;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion.TimeToLoadingCriterion;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion.VehicleUtilizationCriterion;

import java.util.List;

/**
 * Domyślny zestaw kryteriów K1–K5 z wagami eksperckimi (suma = 1).
 * Najwyższa waga dla K2 (czas do załadunku) — terminowość jest dla klienta najważniejsza.
 */
public final class DefaultCriteria {

    public static final double K1_WEIGHT = 0.25;
    public static final double K2_WEIGHT = 0.30;
    public static final double K3_WEIGHT = 0.15;
    public static final double K4_WEIGHT = 0.15;
    public static final double K5_WEIGHT = 0.15;

    private DefaultCriteria() {
    }

    public static List<WeightedCriterion> withDefaultWeights() {
        return withWeights(K1_WEIGHT, K2_WEIGHT, K3_WEIGHT, K4_WEIGHT, K5_WEIGHT);
    }

    // Te same kryteria z innymi wagami (np. z konfiguracji albo do analizy wrażliwości)
    public static List<WeightedCriterion> withWeights(double k1, double k2, double k3, double k4, double k5) {
        return List.of(
                new WeightedCriterion(new DistanceToLoadingCriterion(), k1),
                new WeightedCriterion(new TimeToLoadingCriterion(), k2),
                new WeightedCriterion(new RemainingDrivingTodayCriterion(), k3),
                new WeightedCriterion(new StopCountCriterion(), k4),
                new WeightedCriterion(new VehicleUtilizationCriterion(), k5));
    }
}
