package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.dto.DriverLocationRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverResponse;
import pl.khamutouskaya.truckdispatch.dto.DriverStatusRequest;
import pl.khamutouskaya.truckdispatch.service.DriverService;

import java.util.List;

@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Drivers", description = "Drivers, their status and current location")
public class DriverController {

    private final DriverService driverService;

    @GetMapping
    @Operation(summary = "List drivers, optionally filtered by status")
    public List<DriverResponse> findAll(@RequestParam(required = false) DriverStatus status) {
        return driverService.findAll(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get driver by id")
    public DriverResponse findById(@PathVariable Long id) {
        return driverService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create driver")
    public DriverResponse create(@Valid @RequestBody DriverRequest request) {
        return driverService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update driver personal data")
    public DriverResponse update(@PathVariable Long id, @Valid @RequestBody DriverRequest request) {
        return driverService.update(id, request);
    }

    @PatchMapping("/{id}/location")
    @Operation(summary = "Set driver location manually")
    public DriverResponse updateLocation(@PathVariable Long id, @Valid @RequestBody DriverLocationRequest request) {
        return driverService.updateLocation(id, request);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change driver status")
    public DriverResponse updateStatus(@PathVariable Long id, @Valid @RequestBody DriverStatusRequest request) {
        return driverService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete driver (unlinks vehicle; refused if driver has orders)")
    public void delete(@PathVariable Long id) {
        driverService.delete(id);
    }
}
