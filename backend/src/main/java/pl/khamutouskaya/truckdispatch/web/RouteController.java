package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RouteAlgorithm;
import pl.khamutouskaya.truckdispatch.dto.RouteResponse;
import pl.khamutouskaya.truckdispatch.service.RouteQueryService;

@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
@Tag(name = "Routes", description = "Fastest route between two road nodes for a given vehicle (Dijkstra / A*)")
public class RouteController {

    private final RouteQueryService routeQueryService;

    @GetMapping
    @Operation(summary = "Find the fastest route for a vehicle (404: unknown node or vehicle, "
            + "409: no route, e.g. vehicle too high for a viaduct)")
    public RouteResponse findRoute(
            @RequestParam long fromNodeId,
            @RequestParam long toNodeId,
            @Parameter(description = "Vehicle id — its height and weight decide which roads are open")
            @RequestParam long vehicleId,
            @Parameter(description = "true: vehicle with full load (empty weight + capacity); false: empty vehicle")
            @RequestParam(defaultValue = "true") boolean loaded,
            @RequestParam(defaultValue = "ASTAR") RouteAlgorithm algorithm) {
        return routeQueryService.findRoute(fromNodeId, toNodeId, vehicleId, loaded, algorithm);
    }
}
