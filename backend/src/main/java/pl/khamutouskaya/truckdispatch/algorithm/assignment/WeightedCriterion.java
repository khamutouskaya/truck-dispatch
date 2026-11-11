package pl.khamutouskaya.truckdispatch.algorithm.assignment;

/**
 * Kryterium z wagą w ocenie końcowej: score = Σ waga · wartość znormalizowana.
 */
public record WeightedCriterion(Criterion criterion, double weight) {

    public WeightedCriterion {
        if (criterion == null) {
            throw new IllegalArgumentException("Criterion is required");
        }
        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be non-negative: " + criterion.name());
        }
    }
}
