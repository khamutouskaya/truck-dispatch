package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderRequest;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransportOrderService {

    private static final Sort BY_LOADING_DEADLINE = Sort.by("loadingDeadline");
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(OrderStatus.NEW, OrderStatus.ASSIGNED);

    private final TransportOrderRepository transportOrderRepository;
    private final RoadNodeRepository roadNodeRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentService assignmentService;

    // status == null → wszystkie zlecenia
    public List<TransportOrderResponse> findAll(OrderStatus status) {
        List<TransportOrder> orders = status == null
                ? transportOrderRepository.findAll(BY_LOADING_DEADLINE)
                : transportOrderRepository.findByStatus(status, BY_LOADING_DEADLINE);
        return orders.stream()
                .map(TransportOrderResponse::from)
                .toList();
    }

    public TransportOrderResponse findById(Long id) {
        return TransportOrderResponse.from(getOrder(id));
    }

    // Nowe zlecenie powstaje jako NEW i od razu uruchamiany jest algorytm przydziału — w tej samej transakcji.
    // Wynik widać w odpowiedzi: ASSIGNED z kierowcą albo NEW (nikt nie przeszedł filtrów, powody — GET .../assignment)
    @Transactional
    public TransportOrderResponse create(TransportOrderRequest request) {
        TransportOrder order = new TransportOrder();
        applyRequest(order, request);
        TransportOrder saved = transportOrderRepository.save(order);
        assignmentService.assign(saved.getId());
        return TransportOrderResponse.from(saved);
    }

    @Transactional
    public TransportOrderResponse update(Long id, TransportOrderRequest request) {
        TransportOrder order = getOrder(id);
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ConflictException("Only orders in status NEW can be edited; order " + id
                    + " is " + order.getStatus());
        }
        applyRequest(order, request);
        return TransportOrderResponse.from(order);
    }

    // Zlecenia nie są usuwane fizycznie: anulowanie zachowuje historię
    @Transactional
    public TransportOrderResponse cancel(Long id) {
        TransportOrder order = getOrder(id);
        if (!CANCELLABLE_STATUSES.contains(order.getStatus())) {
            throw new ConflictException("Only orders in status NEW or ASSIGNED can be cancelled; order " + id
                    + " is " + order.getStatus());
        }
        order.setStatus(OrderStatus.CANCELLED);
        return TransportOrderResponse.from(order);
    }

    private TransportOrder getOrder(Long id) {
        return transportOrderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Order", id));
    }

    // Sprawdza reguły biznesowe i dopiero potem przenosi dane do encji
    private void applyRequest(TransportOrder order, TransportOrderRequest request) {
        if (request.loadingNodeId().equals(request.unloadingNodeId())) {
            throw new ConflictException("Loading and unloading node must be different");
        }
        RoadNode loadingNode = getNode(request.loadingNodeId());
        RoadNode unloadingNode = getNode(request.unloadingNodeId());

        if (!request.loadingDeadline().isAfter(LocalDateTime.now())) {
            throw new ConflictException("Loading deadline must be in the future");
        }
        if (!request.deliveryDeadline().isAfter(request.loadingDeadline())) {
            throw new ConflictException("Delivery deadline must be after loading deadline");
        }
        // Na razie sprawdzamy tylko, czy taki pojazd w ogóle jest we flocie;
        // czy jest wolny i czy zdąży — decyduje algorytm przydzielania
        boolean vehicleExists = vehicleRepository.existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
                request.requiredVehicleType(), request.cargoWeightKg(), request.cargoVolumeM3());
        if (!vehicleExists) {
            throw new ConflictException("No vehicle of type " + request.requiredVehicleType()
                    + " can carry " + request.cargoWeightKg() + " kg and " + request.cargoVolumeM3() + " m3");
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
