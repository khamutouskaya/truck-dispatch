package pl.khamutouskaya.truckdispatch.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.khamutouskaya.truckdispatch.dto.DriverLocationRequest;
import pl.khamutouskaya.truckdispatch.dto.DriverResponse;
import pl.khamutouskaya.truckdispatch.dto.MyProfileResponse;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.service.DriverService;
import pl.khamutouskaya.truckdispatch.service.TokenService;

import java.util.List;

/**
 * Panel kierowcy (tylko rola DRIVER — SecurityConfig). Id kierowcy jest brane z tokenu (claim driverId),
 * nigdy z adresu — kierowca nie może podejrzeć ani zmienić cudzych danych.
 */
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
@Tag(name = "Driver panel", description = "Logged-in driver's own data (role DRIVER)")
public class MeController {

    private final DriverService driverService;

    @GetMapping
    @Operation(summary = "My profile and my vehicle")
    public MyProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return driverService.findMyProfile(driverId(jwt));
    }

    @GetMapping("/orders")
    @Operation(summary = "My orders, newest first")
    public List<TransportOrderResponse> orders(@AuthenticationPrincipal Jwt jwt) {
        return driverService.findMyOrders(driverId(jwt));
    }

    @PatchMapping("/location")
    @Operation(summary = "Set my location manually (409 during an active order — then the simulator records it)")
    public DriverResponse updateLocation(@AuthenticationPrincipal Jwt jwt,
                                         @Valid @RequestBody DriverLocationRequest request) {
        return driverService.updateMyLocation(driverId(jwt), request);
    }

    private static Long driverId(Jwt jwt) {
        Object claim = jwt.getClaim(TokenService.DRIVER_ID_CLAIM);
        if (!(claim instanceof Number id)) {
            // Rola DRIVER bez driverId — token niezgodny z kontem (nie powinno się zdarzyć dla tokenów z logowania)
            throw new AccessDeniedException("Token has no driverId");
        }
        return id.longValue();
    }
}
