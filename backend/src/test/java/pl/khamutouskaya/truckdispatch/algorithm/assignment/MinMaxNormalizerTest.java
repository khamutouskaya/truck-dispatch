package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MinMaxNormalizerTest {

    @Test
    void lowerIsBetterGivesOneToMinimum() {
        // K1 z przykładu: 135, 140, 200 km
        assertThat(MinMaxNormalizer.normalize(new double[]{135, 140, 200}, false))
                .containsExactly(1.0, 60.0 / 65, 0.0);
    }

    @Test
    void higherIsBetterGivesOneToMaximum() {
        assertThat(MinMaxNormalizer.normalize(new double[]{2, 4, 6}, true))
                .containsExactly(0.0, 0.5, 1.0);
    }

    @Test
    void equalValuesGiveOneToEveryone() {
        assertThat(MinMaxNormalizer.normalize(new double[]{0.75, 0.75}, true)).containsExactly(1.0, 1.0);
        assertThat(MinMaxNormalizer.normalize(new double[]{3}, false)).containsExactly(1.0);
    }

    @Test
    void emptyInputGivesEmptyOutput() {
        assertThat(MinMaxNormalizer.normalize(new double[0], true)).isEmpty();
    }
}
