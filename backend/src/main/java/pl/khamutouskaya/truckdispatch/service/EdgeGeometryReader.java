package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Odczyt geometrii krawędzi z kolumny jsonb road_edge.geometry ([[lon, lat], ...], kolejność GeoJSON).
 * Wspólne dla symulatora, samouczenia i API tras.
 */
@Component
@RequiredArgsConstructor
class EdgeGeometryReader {

    private final JsonMapper jsonMapper;

    // Bez geometrii — odcinek prosty między węzłami (relacje fromNode/toNode: wywołać w transakcji)
    List<GeoPoint> read(RoadEdge edge) {
        if (edge.getGeometry() == null) {
            return List.of(point(edge.getFromNode()), point(edge.getToNode()));
        }
        double[][] lonLat = jsonMapper.readValue(edge.getGeometry(), double[][].class);
        List<GeoPoint> points = new ArrayList<>(lonLat.length);
        for (double[] p : lonLat) {
            points.add(new GeoPoint(p[1], p[0]));
        }
        return points;
    }

    static GeoPoint point(RoadNode node) {
        return new GeoPoint(node.getLat(), node.getLon());
    }
}
