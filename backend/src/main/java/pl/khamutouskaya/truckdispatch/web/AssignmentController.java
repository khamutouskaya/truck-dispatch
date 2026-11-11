package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision;
import pl.khamutouskaya.truckdispatch.service.AssignmentService;

// Algorytm przydziału dla zlecenia. Automatycznie uruchamia się też przy POST /api/orders.
@RestController
@RequestMapping("/api/orders/{orderId}")
@RequiredArgsConstructor
@Tag(name = "Assignment", description = "Automatic driver assignment with explanation")
public class AssignmentController {

    private final AssignmentService assignmentService;

    // 200, nie 201: to akcja na istniejącym zleceniu (jak PATCH .../cancel), a nie utworzenie nowego zasobu
    @PostMapping("/assign")
    @Operation(summary = "Run assignment algorithm again (only NEW orders); saves the decision")
    public AssignmentDecision assign(@PathVariable Long orderId) {
        return assignmentService.assign(orderId);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Preview ranking and rejection reasons without assigning (only NEW orders)")
    public AssignmentDecision candidates(@PathVariable Long orderId) {
        return assignmentService.evaluate(orderId);
    }

    @GetMapping("/assignment")
    @Operation(summary = "Latest saved assignment decision (ranking with reasons) for the order details screen")
    public AssignmentDecision latestDecision(@PathVariable Long orderId) {
        return assignmentService.latestDecision(orderId);
    }
}
