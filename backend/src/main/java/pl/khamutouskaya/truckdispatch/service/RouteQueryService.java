package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.EdgeTimeCalculator;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphNode;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RouteAlgorithm;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.dto.RouteResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Podgląd trasy między dwoma węzłami dla wybranego pojazdu (GET /api/routes) — mapa w React
 * i porównanie Dijkstra / A*. Liczy na grafie w pamięci; z bazy czyta tylko pojazd i geometrię krawędzi.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RouteQueryService {

    private final RoadGraphService roadGraphService;
    private final VehicleRepository vehicleRepository;
    private final RoadEdgeRepository roadEdgeRepository;
    private final EdgeGeometryReader edgeGeometryReader;

    /**
     * @param loaded true — pojazd z pełnym ładunkiem (masa = masa własna + ładowność): najcięższy przypadek,
     *               więc trasa jest dozwolona dla każdego ładunku tego pojazdu; false — pojazd pusty
     */
    public RouteResponse findRoute(long fromNodeId, long toNodeId, long vehicleId, boolean loaded,
                                   RouteAlgorithm algorithm) {
        RoadGraph graph = roadGraphService.graph();
        requireNode(graph, fromNodeId);
        requireNode(graph, toNodeId);
        Vehicle vehicle = vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new NotFoundException("Vehicle", vehicleId));

        VehicleProfile profile = loaded
                ? VehicleProfile.loaded(vehicle.getHeightM(), vehicle.getEmptyWeightKg(), vehicle.getCapacityKg())
                : VehicleProfile.empty(vehicle.getHeightM(), vehicle.getEmptyWeightKg());

        Route route = algorithm.finder().findRoute(graph, fromNodeId, toNodeId, profile)
                .orElseThrow(() -> new ConflictException(String.format(Locale.ROOT,
                        "No route from %s to %s for vehicle %s (height %.1f m, weight %.1f t): "
                                + "roads are closed by height or weight limits",
                        graph.node(fromNodeId).name(), graph.node(toNodeId).name(),
                        vehicle.getRegistrationNumber(), profile.heightM(), profile.totalWeightT())));

        return toResponse(graph, route, vehicleId, loaded, algorithm, profile);
    }

    private RouteResponse toResponse(RoadGraph graph, Route route, long vehicleId, boolean loaded,
                                     RouteAlgorithm algorithm, VehicleProfile profile) {
        List<RouteResponse.RouteNode> nodes = new ArrayList<>();
        nodes.add(node(graph.node(route.startNodeId())));
        List<RouteResponse.RouteEdge> edges = new ArrayList<>();
        for (GraphEdge e : route.edges()) {
            nodes.add(node(graph.node(e.toId())));
            edges.add(new RouteResponse.RouteEdge(e.id(), e.fromId(), e.toId(), e.distanceKm(),
                    EdgeTimeCalculator.travelTimeMin(e, profile), e.roadType(), e.delayFactor()));
        }
        return new RouteResponse(algorithm, vehicleId, loaded, route.totalTimeMin(), route.distanceKm(),
                route.settledNodeCount(), nodes, edges, geometry(graph, route));
    }

    // Geometrie krawędzi sklejone w jedną linię; punkt styku krawędzi występuje raz
    private List<double[]> geometry(RoadGraph graph, Route route) {
        if (route.edges().isEmpty()) {
            GraphNode only = graph.node(route.startNodeId());
            return List.of(new double[]{only.lat(), only.lon()});
        }
        Map<Long, RoadEdge> byId = roadEdgeRepository.findAllById(route.edges().stream().map(GraphEdge::id).toList())
                .stream().collect(Collectors.toMap(RoadEdge::getId, Function.identity()));
        List<double[]> line = new ArrayList<>();
        for (GraphEdge e : route.edges()) {
            List<GeoPoint> points = edgeGeometryReader.read(byId.get(e.id()));
            for (int i = line.isEmpty() ? 0 : 1; i < points.size(); i++) {
                line.add(new double[]{points.get(i).lat(), points.get(i).lon()});
            }
        }
        return line;
    }

    private static RouteResponse.RouteNode node(GraphNode n) {
        return new RouteResponse.RouteNode(n.id(), n.name(), n.lat(), n.lon());
    }

    private static void requireNode(RoadGraph graph, long nodeId) {
        if (!graph.containsNode(nodeId)) {
            throw new NotFoundException("Road node", nodeId);
        }
    }
}
