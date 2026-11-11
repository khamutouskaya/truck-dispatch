package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import java.util.Map;

/**
 * Ocena kandydata wraz z danymi do wyjaśnienia decyzji.
 *
 * @param candidate        kandydat
 * @param rawValues        surowe wartości kryteriów (nazwa → wartość)
 * @param normalizedValues wartości po normalizacji min-max, 0..1 (nazwa → wartość)
 * @param score            Σ waga · wartość znormalizowana, 0..1
 */
public record CandidateScore(
        Candidate candidate,
        Map<String, Double> rawValues,
        Map<String, Double> normalizedValues,
        double score
) {
    public CandidateScore {
        rawValues = Map.copyOf(rawValues);
        normalizedValues = Map.copyOf(normalizedValues);
    }
}
