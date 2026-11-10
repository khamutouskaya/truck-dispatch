package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

import java.time.LocalDateTime;

public record TransportOrderResponse(
        Long id,
        Long loadingNodeId,
        String loadingNodeName,
        Long unloadingNodeId,
        String unloadingNodeName,
        int cargoWeightKg,
        double cargoVolumeM3,
        VehicleType requiredVehicleType,
        LocalDateTime loadingDeadline,
        LocalDateTime deliveryDeadline,
        Integer estimatedLoadingMin,
        Integer estimatedUnloadingMin,
        OrderStatus status,
        Long assignedDriverId,
        String assignedDriverName,
        LocalDateTime createdAt
) {
    // Wywoływać wewnątrz transakcji: punkty i kierowca to relacje LAZY
    public static TransportOrderResponse from(TransportOrder order) {
        Driver driver = order.getAssignedDriver();
        return new TransportOrderResponse(
                order.getId(),
                order.getLoadingNode().getId(),
                order.getLoadingNode().getName(),
                order.getUnloadingNode().getId(),
                order.getUnloadingNode().getName(),
                order.getCargoWeightKg(),
                order.getCargoVolumeM3(),
                order.getRequiredVehicleType(),
                order.getLoadingDeadline(),
                order.getDeliveryDeadline(),
                order.getEstimatedLoadingMin(),
                order.getEstimatedUnloadingMin(),
                order.getStatus(),
                driver == null ? null : driver.getId(),
                driver == null ? null : driver.getFirstName() + " " + driver.getLastName(),
                order.getCreatedAt()
        );
    }
}
