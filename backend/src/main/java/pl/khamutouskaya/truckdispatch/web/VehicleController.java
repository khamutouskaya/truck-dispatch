package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.VehicleRequest;
import pl.khamutouskaya.truckdispatch.dto.VehicleResponse;
import pl.khamutouskaya.truckdispatch.service.VehicleService;

import java.util.List;

@RestController
@RequestMapping("/api/vehicles")
@RequiredArgsConstructor
@Tag(name = "Vehicles", description = "Trucks and their assignment to drivers")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping
    @Operation(summary = "List all vehicles")
    public List<VehicleResponse> findAll() {
        return vehicleService.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get vehicle by id")
    public VehicleResponse findById(@PathVariable Long id) {
        return vehicleService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create vehicle")
    public VehicleResponse create(@Valid @RequestBody VehicleRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update vehicle")
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest request) {
        return vehicleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete vehicle")
    public void delete(@PathVariable Long id) {
        vehicleService.delete(id);
    }
}
