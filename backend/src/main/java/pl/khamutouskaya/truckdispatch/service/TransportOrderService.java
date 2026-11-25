package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderRequest;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.ErrorCode;
import pl.khamutouskaya.truckdispatch.dto.OrderTrackResponse;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportOrderService {

    private static final Sort BY_LOADING_DEADLINE = Sort.by("loadingDeadline");
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(OrderStatus.NEW, OrderStatus.ASSIGNED);
    private static final Set<OrderStatus> ACTIVE_ORDER_STATUSES = Set.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);

    private final TransportOrderRepository transportOrderRepository;
    private final RoadNodeRepository roadNodeRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentService assignmentService;
    private final DriverLocationRepository driverLocationRepository;

    // status == null → wszystkie zlecenia
    public List<TransportOrderResponse> findAll(OrderStatus status) {
        List<TransportOrder> orders = status == null
                ? transportOrderRepository.findAll(BY_LOADING_DEADLINE)
                : transportOrderRepository.findByStatus(status, BY_LOADING_DEADLINE);
        // Jedno zapytanie o pojazdy dla całej listy zamiast osobnego dla każdego zlecenia
        Map<Long, String> registrationByDriverId = vehicleRepository.findAll().stream()
                .filter(vehicle -> vehicle.getDriver() != null)
                .collect(Collectors.toMap(vehicle -> vehicle.getDriver().getId(), Vehicle::getRegistrationNumber));
        return orders.stream()
                .map(order -> TransportOrderResponse.from(order, registrationOf(order, registrationByDriverId)))
                .toList();
    }

    public TransportOrderResponse findById(Long id) {
        return toResponse(getOrder(id));
    }

    // Nowe zlecenie powstaje jako NEW i od razu uruchamiany jest algorytm przydziału — w tej samej transakcji.
    // Wynik widać w odpowiedzi: ASSIGNED z kierowcą albo NEW (nikt nie przeszedł filtrów, powody — GET .../assignment)
    @Transactional
    public TransportOrderResponse create(TransportOrderRequest request) {
        TransportOrder order = new TransportOrder();
        applyRequest(order, request);
        TransportOrder saved = transportOrderRepository.save(order);
        assignmentService.assign(saved.getId());
        return toResponse(saved);
    }

    @Transactional
    public TransportOrderResponse update(Long id, TransportOrderRequest request) {
        TransportOrder order = getOrder(id);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ConflictException(ErrorCode.ORDER_NOT_EDITABLE,
                    "Only orders in status NEW can be edited; order " + id + " is " + order.getStatus(),
                    Map.of("status", order.getStatus().name()));
        }
        applyRequest(order, request);
        return toResponse(order);
    }

    // Zlecenia nie są usuwane fizycznie: anulowanie zachowuje historię
    @Transactional
    public TransportOrderResponse cancel(Long id) {
        TransportOrder order = getOrder(id);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ConflictException(ErrorCode.ORDER_NOT_CANCELLABLE,
                    "Only orders in status NEW or ASSIGNED can be cancelled; order " + id + " is " + order.getStatus(),
                    Map.of("status", order.getStatus().name()));
        }
        order.setStatus(OrderStatus.CANCELLED);
        releaseDriver(order.getAssignedDriver(), id);
        return toResponse(order);
    }

    /**
     * Przydział ustawił kierowcę na ON_ROUTE — po anulowaniu wraca do AVAILABLE i może dostać kolejne zlecenie.
     * Kierowca zostaje w zleceniu (historia: komu je przydzielono). Status zmieniamy tylko, gdy kierowca nie ma
     * innego aktywnego zlecenia i wciąż jest ON_ROUTE (RESTING / OFF_DUTY ustawione ręcznie zostają).
     */
    private void releaseDriver(Driver driver, Long cancelledOrderId) {
        if (driver == null || driver.getStatus() != DriverStatus.ON_ROUTE) {
            return;
        }
        boolean hasOtherActiveOrder = transportOrderRepository
                .existsByAssignedDriverIdAndStatusInAndIdNot(driver.getId(), ACTIVE_ORDER_STATUSES, cancelledOrderId);
        if (!hasOtherActiveOrder) {
            driver.setStatus(DriverStatus.AVAILABLE);
        }
    }

    // Ślad GPS zlecenia w kolejności czasu (mapa: faktycznie przejechana trasa)
    public OrderTrackResponse findTrack(Long id) {
        TransportOrder order = getOrder(id);
        List<OrderTrackResponse.TrackPoint> points = driverLocationRepository.findByOrderIdOrderByRecordedAtAscIdAsc(id)
                .stream()
                .map(l -> new OrderTrackResponse.TrackPoint(l.getLat(), l.getLon(), l.getRecordedAt(), l.getSource()))
                .toList();
        Long driverId = order.getAssignedDriver() != null ? order.getAssignedDriver().getId() : null;
        return new OrderTrackResponse(order.getId(), order.getStatus(), driverId, points);
    }

    private TransportOrder getOrder(Long id) {
        return transportOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
    }

    // Zlecenie nie przechowuje pojazdu: pokazujemy obecny pojazd przydzielonego kierowcy
    private TransportOrderResponse toResponse(TransportOrder order) {
        Driver driver = order.getAssignedDriver();
        String registration = driver == null
                ? null
                : vehicleRepository.findByDriverId(driver.getId()).map(Vehicle::getRegistrationNumber).orElse(null);
        return TransportOrderResponse.from(order, registration);
    }

    private String registrationOf(TransportOrder order, Map<Long, String> registrationByDriverId) {
        Driver driver = order.getAssignedDriver();
        return driver == null ? null : registrationByDriverId.get(driver.getId());
    }

    private ConflictException noVehicleForCargo(TransportOrderRequest request) {
        List<Vehicle> sameType = vehicleRepository.findByType(request.requiredVehicleType());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("vehicleType", request.requiredVehicleType().name());
        params.put("cargoWeightKg", request.cargoWeightKg());
        params.put("cargoVolumeM3", request.cargoVolumeM3());
        params.put("maxCapacityKg", sameType.stream().map(Vehicle::getCapacityKg)
                .max(Comparator.naturalOrder()).orElse(null));
        params.put("maxVolumeM3", sameType.stream().map(Vehicle::getVolumeM3)
                .max(Comparator.naturalOrder()).orElse(null));
        return new ConflictException(ErrorCode.NO_VEHICLE_FOR_CARGO,
                "No vehicle of type " + request.requiredVehicleType() + " can carry " + request.cargoWeightKg()
                        + " kg and " + request.cargoVolumeM3() + " m3",
                params);
    }

    private void applyRequest(TransportOrder order, TransportOrderRequest request) {
        if (request.loadingNodeId().equals(request.unloadingNodeId())) {
            throw new ConflictException(ErrorCode.SAME_LOADING_AND_UNLOADING,
                    "Loading and unloading node must be different");
        }
        RoadNode loadingNode = getNode(request.loadingNodeId());
        RoadNode unloadingNode = getNode(request.unloadingNodeId());

        if (!request.loadingDeadline().isAfter(LocalDateTime.now())) {
            throw new ConflictException(ErrorCode.LOADING_DEADLINE_NOT_IN_FUTURE,
                    "Loading deadline must be in the future");
        }
        if (!request.deliveryDeadline().isAfter(request.loadingDeadline())) {
            throw new ConflictException(ErrorCode.DELIVERY_NOT_AFTER_LOADING,
                    "Delivery deadline must be after loading deadline");
        }
        // Na razie sprawdzamy tylko, czy taki pojazd w ogóle jest we flocie;
        // czy jest wolny i czy zdąży — decyduje algorytm przydzielania
        boolean vehicleExists = vehicleRepository.existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
                request.requiredVehicleType(), request.cargoWeightKg(), request.cargoVolumeM3());
        if (!vehicleExists) {
            throw noVehicleForCargo(request);
        }

        order.setLoadingNode(loadingNode);
        order.setUnloadingNode(unloadingNode);
        order.setCargoWeightKg(request.cargoWeightKg());
        order.setCargoVolumeM3(request.cargoVolumeM3());
        order.setRequiredVehicleType(request.requiredVehicleType());
        order.setLoadingDeadline(request.loadingDeadline());
        order.setDeliveryDeadline(request.deliveryDeadline());
        order.setEstimatedLoadingMin(request.estimatedLoadingMin());
        order.setEstimatedUnloadingMin(request.estimatedUnloadingMin());
    }

    private RoadNode getNode(Long id) {
        return roadNodeRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Road node", id));
    }
}
