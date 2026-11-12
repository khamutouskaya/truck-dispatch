package pl.khamutouskaya.truckdispatch.algorithm.learning;

/**
 * Samouczenie poprawki czasu krawędzi (wygładzanie wykładnicze):
 * <pre>
 *   delayFactor_new = (1 − α) · delayFactor_old + α · (czas rzeczywisty / czas bazowy),  α = 0,2
 * </pre>
 * Czas bazowy = czas z formuły BEZ delayFactor. Gdyby w mianowniku była prognoza z delayFactor,
 * punkt stały spełniałby d = 0,8·d + 0,2·T/d, czyli d² = T — system nauczyłby się √T zamiast T
 * (np. 1,14 zamiast 1,30). Z czasem bazowym: d = 0,8·d + 0,2·T → d = T.
 * <p>
 * α = 0,2: nowy pomiar ma wagę 20%, historia 80% — pojedynczy nietypowy przejazd (wypadek, szum) nie przestawia
 * krawędzi, a po n pomiarach pozostaje 0,8ⁿ starej wartości (po 10 przejazdach ~11%, po 20 ~1%).
 */
public final class DelayFactorLearner {

    public static final double ALPHA = 0.2;

    private DelayFactorLearner() {
    }

    public static double update(double oldDelayFactor, double actualMin, double baseMin) {
        if (oldDelayFactor <= 0 || actualMin <= 0 || baseMin <= 0) {
            throw new IllegalArgumentException("Delay factor and times must be positive");
        }
        return (1 - ALPHA) * oldDelayFactor + ALPHA * (actualMin / baseMin);
    }
}
