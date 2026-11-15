package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.RoadType;

import java.util.List;

/**
 * Krawędź grafu dla mapy w React. Każda droga to dwie krawędzie (po jednej na kierunek)
 * z osobnym delayFactor — samouczenie działa w każdym kierunku osobno.
 *
 * @param delayFactor    wyuczony współczynnik opóźnienia (1.0 = brak opóźnień, 1.3 = o 30% wolniej)
 * @param geometryLatLon przebieg drogi: [[lat, lon], ...] (kolejność jak w Leaflet), od węzła from do węzła to
 */
public record RoadEdgeResponse(
        long id,
        long fromNodeId,
        String fromNodeName,
        long toNodeId,
        String toNodeName,
        double distanceKm,
        RoadType roadType,
        double delayFactor,
        List<double[]> geometryLatLon
) {
}
