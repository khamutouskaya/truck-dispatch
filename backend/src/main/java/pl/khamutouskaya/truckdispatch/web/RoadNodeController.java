package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.RoadNodeResponse;
import pl.khamutouskaya.truckdispatch.service.RoadNodeService;

import java.util.List;

@RestController
@RequestMapping("/api/nodes")
@RequiredArgsConstructor
@Tag(name = "Road nodes", description = "Cities and junctions on the map (read-only)")
public class RoadNodeController {

    private final RoadNodeService roadNodeService;

    @GetMapping
    @Operation(summary = "List all road nodes")
    public List<RoadNodeResponse> findAll() {
        return roadNodeService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get road node by id")
    public RoadNodeResponse findById(@PathVariable Long id) {
        return roadNodeService.findById(id);
    }
}
