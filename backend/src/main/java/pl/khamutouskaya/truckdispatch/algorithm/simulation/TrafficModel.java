package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import java.util.Map;
import java.util.Random;

/**
 * „Prawdziwy świat” symulacji — ukryty przed algorytmem przydziału.
 * <p>
 * Rzeczywisty czas krawędzi = czas bazowy (formuła) × prawdziwy współczynnik krawędzi × szum.
 * <ul>
 *   <li>prawdziwy współczynnik: stałe korki na wybranych krawędziach (np. A2 przy Warszawie × 1,3), domyślnie 1,0;</li>
 *   <li>szum: losowy, równomierny w [1 − noise, 1 + noise] — każdy przejazd jest trochę inny.</li>
 * </ul>
 * Samouczenie powinno odkryć prawdziwe współczynniki, widząc tylko ślady GPS. Szum ma średnią 1,
 * więc delayFactor dąży do prawdziwego współczynnika, a nie do wartości szumu.
 * Ziarno generatora (seed) czyni eksperyment powtarzalnym.
 */
public final class TrafficModel {

    private final Map<Long, Double> trueFactorByEdgeId;
    private final double noise;
    private final Random random;

    public TrafficModel(Map<Long, Double> trueFactorByEdgeId, double noise, Random random) {
        if (noise < 0 || noise >= 1) {
            throw new IllegalArgumentException("Noise must be in [0, 1)");
        }
        trueFactorByEdgeId.forEach((edgeId, factor) -> {
            if (factor <= 0) {
                throw new IllegalArgumentException("True factor of edge " + edgeId + " must be positive");
            }
        });
        this.trueFactorByEdgeId = Map.copyOf(trueFactorByEdgeId);
        this.noise = noise;
        this.random = random;
    }

    // Świat bez korków i bez szumu: rzeczywistość = formuła
    public static TrafficModel ideal() {
        return new TrafficModel(Map.of(), 0, new Random(0));
    }

    public double trueFactor(long edgeId) {
        return trueFactorByEdgeId.getOrDefault(edgeId, 1.0);
    }

    // Rzeczywisty czas jednego przejazdu krawędzi [min]; każde wywołanie losuje nowy szum
    public double actualTimeMin(long edgeId, double baseTimeMin) {
        double noiseFactor = 1.0 + noise * (2 * random.nextDouble() - 1);
        return baseTimeMin * trueFactor(edgeId) * noiseFactor;
    }
}
