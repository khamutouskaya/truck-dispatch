package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderRequest;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.service.TransportOrderService;

import java.util.List;

// DELETE нет намеренно: заказы хранятся как история, вместо удаления — cancel
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Transport orders", description = "Orders for cargo transport")
public class TransportOrderController {

    private final TransportOrderService transportOrderService;

    @GetMapping
    @Operation(summary = "List orders, optionally filtered by status")
    public List<TransportOrderResponse> findAll(@RequestParam(required = false) OrderStatus status) {
        return transportOrderService.findAll(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by id")
    public TransportOrderResponse findById(@PathVariable Long id) {
        return transportOrderService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create order (always NEW, without driver)")
    public TransportOrderResponse create(@Valid @RequestBody TransportOrderRequest request) {
        return transportOrderService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update order (only in status NEW)")
    public TransportOrderResponse update(@PathVariable Long id, @Valid @RequestBody TransportOrderRequest request) {
        return transportOrderService.update(id, request);
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel order (only NEW or ASSIGNED)")
    public TransportOrderResponse cancel(@PathVariable Long id) {
        return transportOrderService.cancel(id);
    }
}
