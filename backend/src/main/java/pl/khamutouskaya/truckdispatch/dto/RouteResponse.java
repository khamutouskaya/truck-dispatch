package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.algorithm.graph.RouteAlgorithm;
import pl.khamutouskaya.truckdispatch.domain.RoadType;

import java.util.List;

/**
 * Najszybsza trasa między dwoma węzłami dla konkretnego pojazdu — do podglądu na mapie w React.
 *
 * @param algorithm        użyty algorytm
 * @param loaded           czy liczono dla pojazdu z ładunkiem (masa = masa własna + pełna ładowność)
 * @param totalTimeMin     czas przejazdu [min]
 * @param totalDistanceKm  długość [km]
 * @param settledNodeCount liczba węzłów zamkniętych przez algorytm (A* zamyka mniej niż Dijkstra)
 * @param nodes            węzły trasy po kolei, od startu do celu
 * @param edges            krawędzie trasy po kolei
 * @param geometryLatLon   linia trasy dla mapy: [[lat, lon], ...] (kolejność jak w Leaflet)
 */
public record RouteResponse(
        RouteAlgorithm algorithm,
        long vehicleId,
        boolean loaded,
        double totalTimeMin,
        double totalDistanceKm,
        int settledNodeCount,
        List<RouteNode> nodes,
        List<RouteEdge> edges,
        List<double[]> geometryLatLon
) {

    public record RouteNode(long id, String name, double lat, double lon) {
    }

    /**
     * @param timeMin     czas przejazdu krawędzi dla tego pojazdu, z delayFactor [min]
     * @param delayFactor wyuczony współczynnik opóźnienia krawędzi (1.0 = brak opóźnień)
     */
    public record RouteEdge(long id, long fromNodeId, long toNodeId, double distanceKm, double timeMin,
                            RoadType roadType, double delayFactor) {
    }
}
