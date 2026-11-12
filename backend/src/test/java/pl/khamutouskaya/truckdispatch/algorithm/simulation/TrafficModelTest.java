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
    void consecutiveTripsGetIndependentNoise() {
        // Regresja: przy new Random(seed * 31 + tripId) pierwsze losowania sąsiednich podróży były prawie równe
        // i 20 podróży w eksperymencie 8.5 miało identyczny czas. Szum kolejnych podróży musi się różnić.
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (long tripId = 1; tripId <= 20; tripId++) {
            TrafficModel traffic = new TrafficModel(Map.of(), 0.10, TrafficModel.randomFor(42, tripId));
            double t = traffic.actualTimeMin(1, 100);
            min = Math.min(min, t);
            max = Math.max(max, t);
        }
        // 20 niezależnych losowań z [90, 110] — rozrzut znacznie większy niż 1 min
        assertThat(max - min).isGreaterThan(10);
    }

    @Test
    void sameTripGetsSameNoiseAfterRestart() {
        double first = new TrafficModel(Map.of(), 0.1, TrafficModel.randomFor(42, 7)).actualTimeMin(1, 60);
        double again = new TrafficModel(Map.of(), 0.1, TrafficModel.randomFor(42, 7)).actualTimeMin(1, 60);

        assertThat(again).isEqualTo(first);
    }

    @Test
    void rejectsInvalidParameters() {
        assertThatThrownBy(() -> new TrafficModel(Map.of(), 1.0, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TrafficModel(Map.of(1L, 0.0), 0.1, new Random()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
