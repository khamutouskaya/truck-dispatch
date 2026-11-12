package pl.khamutouskaya.truckdispatch.algorithm.learning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DelayFactorLearnerTest {

    @Test
    void oneUpdateMovesTwentyPercentTowardsMeasuredRatio() {
        // 0,8 · 1,0 + 0,2 · (130 / 100) = 1,06
        assertThat(DelayFactorLearner.update(1.0, 130, 100)).isCloseTo(1.06, within(1e-12));
    }

    @Test
    void convergesToTrueCongestion() {
        // Prawdziwy korek × 1,3: po n przejazdach zostaje 0,8ⁿ różnicy → 1,3 − 0,3 · 0,8ⁿ
        double d = 1.0;
        for (int n = 1; n <= 20; n++) {
            d = DelayFactorLearner.update(d, 130, 100);
            assertThat(d).isCloseTo(1.3 - 0.3 * Math.pow(0.8, n), within(1e-12));
        }
        assertThat(d).isCloseTo(1.3, within(0.005)); // po 20 przejazdach: 1,2965
    }

    @Test
    void dividingByForecastInsteadOfBaseTimeWouldLearnSquareRoot() {
        // Pułapka: mianownik z delayFactor (prognoza 100·d) → punkt stały d² = 1,3 → d = 1,140, a nie 1,3
        double d = 1.0;
        for (int n = 0; n < 200; n++) {
            d = DelayFactorLearner.update(d, 130, 100 * d);
        }
        assertThat(d).isCloseTo(Math.sqrt(1.3), within(1e-9));
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThatThrownBy(() -> DelayFactorLearner.update(1.0, 0, 100)).isInstanceOf(IllegalArgumentException.class);
    }
}
