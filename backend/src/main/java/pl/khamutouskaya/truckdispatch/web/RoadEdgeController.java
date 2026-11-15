package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.RoadEdgeResponse;
import pl.khamutouskaya.truckdispatch.service.RoadEdgeService;

import java.util.List;

@RestController
@RequestMapping("/api/edges")
@RequiredArgsConstructor
@Tag(name = "Road edges", description = "Directed road segments with geometry for the map (read-only)")
public class RoadEdgeController {

    private final RoadEdgeService roadEdgeService;

    @GetMapping
    @Operation(summary = "List all road edges with geometry [lat, lon], road type and learned delayFactor")
    public List<RoadEdgeResponse> findAll() {
        return roadEdgeService.findAll();
    }
}
