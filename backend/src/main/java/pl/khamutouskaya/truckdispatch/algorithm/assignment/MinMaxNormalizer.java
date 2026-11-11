package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import java.util.Arrays;

/**
 * Normalizacja min-max do przedziału [0, 1] względem zbioru kandydatów:
 * <ul>
 *   <li>„większa lepsza”: (x − min) / (max − min);</li>
 *   <li>„mniejsza lepsza”: (max − x) / (max − min);</li>
 *   <li>max = min (kryterium nikogo nie różnicuje): wszyscy dostają 1.</li>
 * </ul>
 * Najlepszy kandydat w danym kryterium dostaje 1, najgorszy 0.
 */
public final class MinMaxNormalizer {

    // Tolerancja: różnice mniejsze uznajemy za równość (szum liczb zmiennoprzecinkowych)
    private static final double EPS = 1e-9;

    private MinMaxNormalizer() {
    }

    public static double[] normalize(double[] values, boolean higherIsBetter) {
        if (values.length == 0) {
            return new double[0];
        }
        double min = Arrays.stream(values).min().orElseThrow();
        double max = Arrays.stream(values).max().orElseThrow();
        double range = max - min;

        double[] result = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            if (range <= EPS) {
                result[i] = 1.0;
            } else if (higherIsBetter) {
                result[i] = (values[i] - min) / range;
            } else {
                result[i] = (max - values[i]) / range;
            }
        }
        return result;
    }
}
