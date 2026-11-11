package pl.khamutouskaya.truckdispatch.algorithm.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

// Wartości oczekiwane policzone ręcznie ze wzoru (zob. docs/karta-i-algorytm.md, część 1.4 i 2.1)
class EdgeTimeCalculatorTest {

    private static final double EPS = 1e-9;

    // Ciągnik 4,0 m, masa własna 14 t, ładunek 18 t
    private static final VehicleProfile EMPTY_TRUCK = VehicleProfile.empty(4.0, 14000);
    private static final VehicleProfile LOADED_TRUCK = VehicleProfile.loaded(4.0, 14000, 18000);

    @Test
    void motorwayExampleFromDocumentation() {
        // Warszawa → Łódź, A2: 135 km, v = 80 × 1 × 1 = 80 km/h, t = 135 / 80 h = 1,6875 h = 101,25 min
        GraphEdge a2 = edge(135, RoadType.MOTORWAY, SurfaceQuality.GOOD, Terrain.FLAT, 1.0);

        assertThat(EdgeTimeCalculator.effectiveSpeedKmh(a2, EMPTY_TRUCK)).isCloseTo(80.0, within(EPS));
        assertThat(EdgeTimeCalculator.travelTimeMin(a2, EMPTY_TRUCK)).isCloseTo(101.25, within(EPS));
    }

    @Test
    void nationalRoadWithAverageSurfaceFromDocumentation() {
        // 70 km, v = 65 × 0,85 = 55,25 km/h, t = 70 / 55,25 × 60 = 76,0181 min (w dokumentacji 1,27 h)
        GraphEdge national = edge(70, RoadType.NATIONAL, SurfaceQuality.AVERAGE, Terrain.FLAT, 1.0);

        assertThat(EdgeTimeCalculator.effectiveSpeedKmh(national, EMPTY_TRUCK)).isCloseTo(55.25, within(EPS));
        assertThat(EdgeTimeCalculator.travelTimeMin(national, EMPTY_TRUCK)).isCloseTo(70 / 55.25 * 60, within(EPS));
    }

    @Test
    void loadedTruckIsSlowerInMountainsButNotOnFlat() {
        GraphEdge mountain = edge(65, RoadType.NATIONAL, SurfaceQuality.GOOD, Terrain.MOUNTAIN, 1.0);
        GraphEdge flat = edge(65, RoadType.NATIONAL, SurfaceQuality.GOOD, Terrain.FLAT, 1.0);

        // pusty: 65 × 0,75 = 48,75 km/h → 80 min; załadowany: 65 × 0,65 = 42,25 km/h → 92,3077 min
        assertThat(EdgeTimeCalculator.travelTimeMin(mountain, EMPTY_TRUCK)).isCloseTo(80.0, within(EPS));
        assertThat(EdgeTimeCalculator.travelTimeMin(mountain, LOADED_TRUCK)).isCloseTo(65 / 42.25 * 60, within(EPS));
        // na płaskim teren nie zależy od załadunku: 60 min w obu przypadkach
        assertThat(EdgeTimeCalculator.travelTimeMin(flat, EMPTY_TRUCK)).isCloseTo(60.0, within(EPS));
        assertThat(EdgeTimeCalculator.travelTimeMin(flat, LOADED_TRUCK)).isCloseTo(60.0, within(EPS));
    }

    @Test
    void delayFactorScalesTime() {
        GraphEdge slow = edge(135, RoadType.MOTORWAY, SurfaceQuality.GOOD, Terrain.FLAT, 1.2);

        // 101,25 × 1,2 = 121,5 min
        assertThat(EdgeTimeCalculator.travelTimeMin(slow, EMPTY_TRUCK)).isCloseTo(121.5, within(EPS));
    }

    @ParameterizedTest
    @CsvSource({
            "MOTORWAY,   80",
            "EXPRESSWAY, 75",
            "NATIONAL,   65",
            "REGIONAL,   55",
            "LOCAL,      45"
    })
    void baseSpeedPerRoadType(RoadType roadType, double expectedKmh) {
        assertThat(EdgeTimeCalculator.baseSpeedKmh(roadType)).isEqualTo(expectedKmh);
    }

    @ParameterizedTest
    @CsvSource({
            "FLAT,     false, 1.0",
            "FLAT,     true,  1.0",
            "HILLY,    false, 0.9",
            "HILLY,    true,  0.85",
            "MOUNTAIN, false, 0.75",
            "MOUNTAIN, true,  0.65"
    })
    void terrainFactorDependsOnLoad(Terrain terrain, boolean loaded, double expected) {
        assertThat(EdgeTimeCalculator.terrainFactor(terrain, loaded)).isEqualTo(expected);
    }

    @Test
    void heightLimitClosesEdgeOnlyForHigherVehicle() {
        GraphEdge viaduct = limitedEdge(null, 3.8);

        assertThat(EdgeTimeCalculator.isClosedFor(viaduct, VehicleProfile.empty(4.0, 14000))).isTrue();
        assertThat(EdgeTimeCalculator.isClosedFor(viaduct, VehicleProfile.empty(3.8, 14000))).isFalse();
        assertThat(EdgeTimeCalculator.isClosedFor(viaduct, VehicleProfile.empty(3.4, 7500))).isFalse();
    }

    @Test
    void weightLimitUsesTotalWeightSoEmptyTruckPassesButLoadedDoesNot() {
        GraphEdge bridge = limitedEdge(20.0, null);

        // pusty: 14 t ≤ 20 t; załadowany: 14 + 18 = 32 t > 20 t
        assertThat(EdgeTimeCalculator.isClosedFor(bridge, EMPTY_TRUCK)).isFalse();
        assertThat(EdgeTimeCalculator.isClosedFor(bridge, LOADED_TRUCK)).isTrue();
    }

    @Test
    void edgeWithoutLimitsIsOpenForEveryVehicle() {
        GraphEdge open = limitedEdge(null, null);

        assertThat(EdgeTimeCalculator.isClosedFor(open, LOADED_TRUCK)).isFalse();
    }

    @Test
    void loadedProfileAddsCargoToEmptyWeight() {
        assertThat(LOADED_TRUCK.totalWeightT()).isEqualTo(32.0);
        assertThat(LOADED_TRUCK.loaded()).isTrue();
        assertThat(EMPTY_TRUCK.totalWeightT()).isEqualTo(14.0);
        assertThat(EMPTY_TRUCK.loaded()).isFalse();
    }

    @Test
    void rejectsNonPositiveVehicleDimensions() {
        assertThatThrownBy(() -> new VehicleProfile(0, 14, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GraphEdge edge(double km, RoadType type, SurfaceQuality surface, Terrain terrain, double delay) {
        return new GraphEdge(1, 1, 2, km, type, surface, terrain, null, null, delay);
    }

    private static GraphEdge limitedEdge(Double maxWeightT, Double maxHeightM) {
        return new GraphEdge(1, 1, 2, 50, RoadType.NATIONAL, SurfaceQuality.GOOD, Terrain.FLAT,
                maxWeightT, maxHeightM, 1.0);
    }
}
