package pl.khamutouskaya.truckdispatch.algorithm.graph;

import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

/**
 * Czas przejazdu krawędzi dla konkretnego pojazdu — waga krawędzi w algorytmach Dijkstry i A*.
 * <pre>
 *   v_eff = v_base(roadType) × k_surface(surfaceQuality) × k_terrain(terrain, loaded)   [km/h]
 *   t     = distanceKm / v_eff × 60 × delayFactor                                     [min]
 * </pre>
 * Wszystkie współczynniki są w tej jednej klasie. Klasa jest bezstanowa (same metody statyczne).
 */
public final class EdgeTimeCalculator {

    private static final double MINUTES_PER_HOUR = 60.0;

    private EdgeTimeCalculator() {
    }

    // Prędkość bazowa ciężarówki na danej klasie drogi [km/h]
    public static double baseSpeedKmh(RoadType roadType) {
        return switch (roadType) {
            case MOTORWAY -> 80;
            case EXPRESSWAY -> 75;
            case NATIONAL -> 65;
            case REGIONAL -> 55;
            case LOCAL -> 45;
        };
    }

    public static double surfaceFactor(SurfaceQuality surfaceQuality) {
        return switch (surfaceQuality) {
            case GOOD -> 1.0;
            case AVERAGE -> 0.85;
            case POOR -> 0.7;
        };
    }

    // Załadowana ciężarówka wolniej jedzie pod górę, dlatego w terenie HILLY/MOUNTAIN współczynnik jest mniejszy
    public static double terrainFactor(Terrain terrain, boolean loaded) {
        return switch (terrain) {
            case FLAT -> 1.0;
            case HILLY -> loaded ? 0.85 : 0.9;
            case MOUNTAIN -> loaded ? 0.65 : 0.75;
        };
    }

    // v_eff [km/h]
    public static double effectiveSpeedKmh(GraphEdge edge, VehicleProfile vehicle) {
        return baseSpeedKmh(edge.roadType())
                * surfaceFactor(edge.surfaceQuality())
                * terrainFactor(edge.terrain(), vehicle.loaded());
    }

    // Czas przejazdu krawędzi [min], bez zaokrąglania (zaokrągla się dopiero przy wyświetlaniu)
    public static double travelTimeMin(GraphEdge edge, VehicleProfile vehicle) {
        return baseTravelTimeMin(edge, vehicle) * edge.delayFactor();
    }

    // Czas „z samej formuły”, bez poprawki delayFactor [min] — mianownik w samouczeniu (fakt / czas bazowy)
    public static double baseTravelTimeMin(GraphEdge edge, VehicleProfile vehicle) {
        return edge.distanceKm() / effectiveSpeedKmh(edge, vehicle) * MINUTES_PER_HOUR;
    }

    // Krawędź zamknięta, jeśli pojazd jest wyższy lub cięższy niż dopuszcza odcinek (null = brak ograniczenia).
    // Wartość równa ograniczeniu jest dopuszczalna.
    public static boolean isClosedFor(GraphEdge edge, VehicleProfile vehicle) {
        boolean tooHigh = edge.maxHeightM() != null && vehicle.heightM() > edge.maxHeightM();
        boolean tooHeavy = edge.maxWeightT() != null && vehicle.totalWeightT() > edge.maxWeightT();
        return tooHigh || tooHeavy;
    }
}
