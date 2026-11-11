package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class TrafficModelTest {

    @Test
    void hiddenFactorMultipliesBaseTime() {
        TrafficModel traffic = new TrafficModel(Map.of(7L, 1.3), 0, new Random(1));

        assertThat(traffic.actualTimeMin(7, 100)).isCloseTo(130, within(1e-9));
        assertThat(traffic.actualTimeMin(8, 100)).isCloseTo(100, within(1e-9)); // bez korka
    }

    @Test
    void noiseStaysInRangeAndAveragesToTrueFactor() {
        TrafficModel traffic = new TrafficModel(Map.of(7L, 1.3), 0.10, new Random(42));
        double sum = 0;
        int n = 10_000;
        for (int i = 0; i < n; i++) {
            double t = traffic.actualTimeMin(7, 100);
            assertThat(t).isBetween(130 * 0.9, 130 * 1.1);
            sum += t;
        }
        // Średnia szumu = 1 → średni czas ≈ 130
        assertThat(sum / n).isCloseTo(130, within(0.5));
    }

    @Test
    void sameSeedGivesSameWorld() {
        TrafficModel first = new TrafficModel(Map.of(), 0.1, new Random(5));
        TrafficModel second = new TrafficModel(Map.of(), 0.1, new Random(5));

        assertThat(first.actualTimeMin(1, 60)).isEqualTo(second.actualTimeMin(1, 60));
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new TrafficModel(Map.of(), 1.0, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrafficModel(Map.of(1L, 0.0), 0.1, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
