package pl.khamutouskaya.truckdispatch.algorithm.learning;

import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;

/**
 * Punkt śladu GPS.
 *
 * @param position położenie
 * @param tMin     chwila pomiaru [min od pierwszego punktu zlecenia]
 */
public record TrackPoint(GeoPoint position, double tMin) {
}
