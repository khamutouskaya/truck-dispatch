package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ocena wielokryterialna kandydatów (kroki 7–9 algorytmu, docs/karta-i-algorytm.md):
 * <ol>
 *   <li>surowe wartości każdego kryterium dla każdego kandydata;</li>
 *   <li>normalizacja min-max osobno w każdym kryterium;</li>
 *   <li>score = Σ wᵢ · normᵢ;</li>
 *   <li>sortowanie: score malejąco, przy remisie wcześniejszy przyjazd na załadunek (K2),
 *       potem mniej km (K1), na końcu mniejsze id kierowcy (wynik zawsze powtarzalny).</li>
 * </ol>
 */
public final class CandidateScorer {

    private static final double WEIGHT_SUM_TOLERANCE = 1e-6;

    private static final Comparator<CandidateScore> RANKING =
            Comparator.comparingDouble(CandidateScore::score).reversed()
                    .thenComparingDouble(s -> s.candidate().simulation().arrivalAtLoadingMin())
                    .thenComparingDouble(s -> s.candidate().distanceToLoadingKm())
                    .thenComparingLong(s -> s.candidate().driverId());

    private CandidateScorer() {
    }

    public static List<CandidateScore> rank(List<Candidate> candidates, TransportOrder order,
                                            List<WeightedCriterion> criteria) {
        validateWeights(criteria);
        int n = candidates.size();

        // Kolumna po kolumnie: surowe wartości jednego kryterium u wszystkich kandydatów, potem normalizacja
        List<Map<String, Double>> raw = new ArrayList<>();
        List<Map<String, Double>> normalized = new ArrayList<>();
        double[] scores = new double[n];
        for (int i = 0; i < n; i++) {
            raw.add(new HashMap<>());
            normalized.add(new HashMap<>());
        }

        for (WeightedCriterion wc : criteria) {
            Criterion criterion = wc.criterion();
            double[] values = new double[n];
            for (int i = 0; i < n; i++) {
                values[i] = criterion.rawValue(candidates.get(i), order);
            }
            double[] norm = MinMaxNormalizer.normalize(values, criterion.higherIsBetter());
            for (int i = 0; i < n; i++) {
                raw.get(i).put(criterion.name(), values[i]);
                normalized.get(i).put(criterion.name(), norm[i]);
                scores[i] += wc.weight() * norm[i];
            }
        }

        List<CandidateScore> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(new CandidateScore(candidates.get(i), raw.get(i), normalized.get(i), scores[i]));
        }
        result.sort(RANKING);
        return result;
    }

    // Wagi muszą sumować się do 1, a nazwy kryteriów być unikalne (są kluczami w wyjaśnieniu)
    public static void validateWeights(List<WeightedCriterion> criteria) {
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("At least one criterion is required");
        }
        double sum = criteria.stream().mapToDouble(WeightedCriterion::weight).sum();
        if (Math.abs(sum - 1.0) > WEIGHT_SUM_TOLERANCE) {
            throw new IllegalArgumentException("Weights must sum to 1, got " + sum);
        }
        long distinctNames = criteria.stream().map(wc -> wc.criterion().name()).distinct().count();
        if (distinctNames != criteria.size()) {
            throw new IllegalArgumentException("Criterion names must be unique");
        }
    }
}
