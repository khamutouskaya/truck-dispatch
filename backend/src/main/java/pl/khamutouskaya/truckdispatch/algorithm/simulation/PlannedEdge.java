package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import java.util.List;

/**
 * Krawędź zaplanowanej trasy w postaci potrzebnej symulatorowi.
 *
 * @param seq         kolejność w trasie zlecenia (order_route_edge.seq)
 * @param edgeId      id krawędzi grafu (klucz do prawdziwego współczynnika w TrafficModel)
 * @param baseTimeMin czas z formuły, bez delayFactor [min]
 * @param geometry    geometria krawędzi od początku do końca (co najmniej 2 punkty)
 */
public record PlannedEdge(int seq, long edgeId, double baseTimeMin, List<GeoPoint> geometry) {

    public PlannedEdge {
        if (baseTimeMin <= 0) {
            throw new IllegalArgumentException("Base time must be positive");
        }
        if (geometry.size() < 2) {
            throw new IllegalArgumentException("Edge geometry needs at least 2 points");
        }
        geometry = List.copyOf(geometry);
    }
}
