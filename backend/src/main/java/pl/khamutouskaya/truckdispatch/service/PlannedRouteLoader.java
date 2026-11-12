package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.PlannedEdge;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.RouteLeg;
import pl.khamutouskaya.truckdispatch.repository.OrderRouteEdgeRepository;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Zaplanowana trasa zlecenia z bazy (order_route_edge) w postaci dla algorytmów: krawędzie z geometrią.
 * Wspólne dla symulatora (po czym jechać) i samouczenia (na co rzutować ślad GPS).
 */
@Component
@RequiredArgsConstructor
class PlannedRouteLoader {

    private final OrderRouteEdgeRepository orderRouteEdgeRepository;
    private final JsonMapper jsonMapper;

    /**
     * @param empty   krawędzie dojazdu pustym
     * @param loaded  krawędzie z ładunkiem
     * @param bySeq   encje trasy po seq — do zapisu wyników samouczenia
     */
    record PlannedRoute(List<PlannedEdge> empty, List<PlannedEdge> loaded, Map<Integer, OrderRouteEdge> bySeq) {

        List<PlannedEdge> all() {
            List<PlannedEdge> all = new ArrayList<>(empty);
            all.addAll(loaded);
            return all;
        }
    }

    PlannedRoute load(Long orderId) {
        List<PlannedEdge> empty = new ArrayList<>();
        List<PlannedEdge> loaded = new ArrayList<>();
        Map<Integer, OrderRouteEdge> bySeq = new LinkedHashMap<>();
        for (OrderRouteEdge routeEdge : orderRouteEdgeRepository.findByOrderIdOrderBySeq(orderId)) {
            PlannedEdge planned = new PlannedEdge(routeEdge.getSeq(), routeEdge.getEdge().getId(),
                    routeEdge.getBaseTimeMin(), geometry(routeEdge.getEdge()));
            (routeEdge.getLeg() == RouteLeg.EMPTY ? empty : loaded).add(planned);
            bySeq.put(routeEdge.getSeq(), routeEdge);
        }
        return new PlannedRoute(empty, loaded, bySeq);
    }

    // Geometria z kolumny jsonb: [[lon, lat], ...]; bez geometrii — odcinek prosty między węzłami
    private List<GeoPoint> geometry(RoadEdge edge) {
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
