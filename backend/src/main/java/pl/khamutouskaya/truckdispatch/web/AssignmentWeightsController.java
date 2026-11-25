package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsRequest;
import pl.khamutouskaya.truckdispatch.dto.AssignmentWeightsResponse;
import pl.khamutouskaya.truckdispatch.service.AssignmentWeightsService;

/**
 * Wagi kryteriów algorytmu przydziału (tylko dyspozytor — reguła /api/** w SecurityConfig).
 * Nowe wagi działają od następnego przydziału; zapisane decyzje mają w JSON wagi, z którymi powstały.
 */
@RestController
@RequestMapping("/api/settings/weights")
@RequiredArgsConstructor
@Tag(name = "Assignment weights", description = "Weights of criteria K1–K5 used by the assignment algorithm")
public class AssignmentWeightsController {

    private final AssignmentWeightsService assignmentWeightsService;

    @GetMapping
    @Operation(summary = "Current weights and defaults from application.yaml")
    public AssignmentWeightsResponse current() {
        return assignmentWeightsService.current();
    }

    @PutMapping
    @Operation(summary = "Save new weights (each 0..1, sum = 1); used from the next assignment")
    public AssignmentWeightsResponse update(@Valid @RequestBody AssignmentWeightsRequest request) {
        return assignmentWeightsService.update(request);
    }

    // 200, nie 201: akcja na ustawieniach (jak PATCH .../cancel), nie nowy zasób dla klienta
    @PostMapping("/reset")
    @Operation(summary = "Restore default weights from application.yaml")
    public AssignmentWeightsResponse reset() {
        return assignmentWeightsService.resetToDefaults();
    }
}
