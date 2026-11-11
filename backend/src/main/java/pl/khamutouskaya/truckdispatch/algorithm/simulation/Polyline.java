package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import pl.khamutouskaya.truckdispatch.algorithm.graph.GeoDistance;

import java.util.List;

/**
 * Linia łamana (geometria krawędzi z openrouteservice): długość i punkt w danym ułamku długości.
 */
public final class Polyline {

    private Polyline() {
    }

    public static double lengthKm(List<GeoPoint> points) {
        double length = 0;
        for (int i = 1; i < points.size(); i++) {
            length += distanceKm(points.get(i - 1), points.get(i));
        }
        return length;
    }

    /**
     * Punkt leżący w ułamku {@code fraction} długości łamanej (0 = początek, 1 = koniec).
     * Między wierzchołkami — interpolacja liniowa współrzędnych (na odcinkach rzędu setek metrów wystarczy).
     */
    public static GeoPoint pointAt(List<GeoPoint> points, double fraction) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("Polyline has no points");
        }
        double clamped = Math.clamp(fraction, 0.0, 1.0);
        double target = lengthKm(points) * clamped;
        double walked = 0;
        for (int i = 1; i < points.size(); i++) {
            GeoPoint a = points.get(i - 1);
            GeoPoint b = points.get(i);
            double segment = distanceKm(a, b);
            if (walked + segment >= target && segment > 0) {
                double t = (target - walked) / segment;
                return new GeoPoint(a.lat() + t * (b.lat() - a.lat()), a.lon() + t * (b.lon() - a.lon()));
            }
            walked += segment;
        }
        return points.getLast();
    }

    static double distanceKm(GeoPoint a, GeoPoint b) {
        return GeoDistance.haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }
}
