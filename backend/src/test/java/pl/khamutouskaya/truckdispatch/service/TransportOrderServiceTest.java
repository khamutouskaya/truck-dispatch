package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderRequest;
import pl.khamutouskaya.truckdispatch.dto.TransportOrderResponse;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.ErrorCode;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Test jednostkowy bez bazy danych i bez Springa: repozytoria zastąpione mockami Mockito
@ExtendWith(MockitoExtension.class)
class TransportOrderServiceTest {

    private static final long WARSZAWA_ID = 1L;
    private static final long GDANSK_ID = 6L;

    @Mock
    private TransportOrderRepository transportOrderRepository;

    @Mock
    private RoadNodeRepository roadNodeRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private AssignmentService assignmentService;

    @InjectMocks
    private TransportOrderService transportOrderService;

    private RoadNode warszawa;
    private RoadNode gdansk;

    @BeforeEach
    void setUp() {
        warszawa = node(WARSZAWA_ID, "Warszawa");
        gdansk = node(GDANSK_ID, "Gdańsk");
    }

    @Test
    void createsOrderWithStatusNewAndWithoutDriver() {
        when(roadNodeRepository.findById(WARSZAWA_ID)).thenReturn(Optional.of(warszawa));
        when(roadNodeRepository.findById(GDANSK_ID)).thenReturn(Optional.of(gdansk));
        when(vehicleRepository.existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
                VehicleType.TILT, 18000, 70.0)).thenReturn(true);
        // save zwraca ten sam obiekt, który otrzymał, z nadanym id
        when(transportOrderRepository.save(any(TransportOrder.class)))
                .thenAnswer(invocation -> {
                    TransportOrder saved = invocation.getArgument(0);
                    saved.setId(42L);
                    return saved;
                });

        TransportOrderResponse response = transportOrderService.create(validRequest());

        assertThat(response.status()).isEqualTo(OrderStatus.NEW);
        assertThat(response.assignedDriverId()).isNull();
        assertThat(response.loadingNodeName()).isEqualTo("Warszawa");
        assertThat(response.unloadingNodeName()).isEqualTo("Gdańsk");
        assertThat(response.cargoWeightKg()).isEqualTo(18000);
        verify(transportOrderRepository).save(any(TransportOrder.class));
        // Algorytm przydziału uruchamia się automatycznie (tu mock — dlatego status zostaje NEW)
        verify(assignmentService).assign(42L);
    }

    @Test
    void rejectsDeliveryDeadlineBeforeLoadingDeadline() {
        when(roadNodeRepository.findById(WARSZAWA_ID)).thenReturn(Optional.of(warszawa));
        when(roadNodeRepository.findById(GDANSK_ID)).thenReturn(Optional.of(gdansk));
        LocalDateTime loading = LocalDateTime.now().plusDays(2);
        TransportOrderRequest request = request(WARSZAWA_ID, GDANSK_ID, loading, loading.minusHours(1));

        assertThatThrownBy(() -> transportOrderService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Delivery deadline must be after loading deadline");
        verify(transportOrderRepository, never()).save(any());
    }

    @Test
    void rejectsSameLoadingAndUnloadingNode() {
        LocalDateTime loading = LocalDateTime.now().plusDays(1);
        TransportOrderRequest request = request(WARSZAWA_ID, WARSZAWA_ID, loading, loading.plusDays(1));

        assertThatThrownBy(() -> transportOrderService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("must be different");
        verify(transportOrderRepository, never()).save(any());
    }

    @Test
    void rejectsOrderWhenNoVehicleCanCarryCargo() {
        when(roadNodeRepository.findById(WARSZAWA_ID)).thenReturn(Optional.of(warszawa));
        when(roadNodeRepository.findById(GDANSK_ID)).thenReturn(Optional.of(gdansk));
        when(vehicleRepository.existsByTypeAndCapacityKgGreaterThanEqualAndVolumeM3GreaterThanEqual(
                VehicleType.TILT, 18000, 70.0)).thenReturn(false);
        Vehicle small = new Vehicle();
        small.setCapacityKg(12000);
        small.setVolumeM3(60.0);
        Vehicle big = new Vehicle();
        big.setCapacityKg(16000);
        big.setVolumeM3(55.0);
        when(vehicleRepository.findByType(VehicleType.TILT)).thenReturn(List.of(small, big));

        assertThatThrownBy(() -> transportOrderService.create(validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No vehicle of type TILT")
                .satisfies(e -> {
                    ConflictException conflict = (ConflictException) e;
                    assertThat(conflict.getCode()).isEqualTo(ErrorCode.NO_VEHICLE_FOR_CARGO);
                    assertThat(conflict.getParams())
                            .containsEntry("vehicleType", "TILT")
                            .containsEntry("maxCapacityKg", 16000)
                            .containsEntry("maxVolumeM3", 60.0);
                });
        verify(transportOrderRepository, never()).save(any());
    }

    @Test
    void rejectsNonExistentNode() {
        when(roadNodeRepository.findById(WARSZAWA_ID)).thenReturn(Optional.of(warszawa));
        when(roadNodeRepository.findById(999L)).thenReturn(Optional.empty());
        LocalDateTime loading = LocalDateTime.now().plusDays(1);
        TransportOrderRequest request = request(WARSZAWA_ID, 999L, loading, loading.plusDays(1));

        assertThatThrownBy(() -> transportOrderService.create(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("999");
        verify(transportOrderRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundForNonExistentOrder() {
        when(transportOrderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportOrderService.findById(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order with id 999 not found");
    }

    @Test
    void throwsNotFoundWhenCancellingNonExistentOrder() {
        when(transportOrderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transportOrderService.cancel(999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order with id 999 not found");
    }

    @Test
    void rejectsCancellingOrderThatIsAlreadyDone() {
        TransportOrder order = existingOrder(10L, OrderStatus.DONE);
        when(transportOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transportOrderService.cancel(10L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("order 10 is DONE");
        // Status nie powinien się zmienić
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);
    }

    @Test
    void cancellingAssignedOrderMakesDriverAvailableAgain() {
        Driver driver = driver(3L, DriverStatus.ON_ROUTE);
        TransportOrder order = existingOrder(10L, OrderStatus.ASSIGNED);
        order.setAssignedDriver(driver);
        when(transportOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(transportOrderRepository.existsByAssignedDriverIdAndStatusInAndIdNot(eq(3L), any(), eq(10L)))
                .thenReturn(false);

        TransportOrderResponse response = transportOrderService.cancel(10L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        // Kierowca zostaje w zleceniu jako historia
        assertThat(response.assignedDriverId()).isEqualTo(3L);
    }

    @Test
    void cancellingKeepsDriverOnRouteWhenHeHasAnotherActiveOrder() {
        Driver driver = driver(3L, DriverStatus.ON_ROUTE);
        TransportOrder order = existingOrder(10L, OrderStatus.ASSIGNED);
        order.setAssignedDriver(driver);
        when(transportOrderRepository.findById(10L)).thenReturn(Optional.of(order));
        when(transportOrderRepository.existsByAssignedDriverIdAndStatusInAndIdNot(eq(3L), any(), eq(10L)))
                .thenReturn(true);

        transportOrderService.cancel(10L);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.ON_ROUTE);
    }

    @Test
    void cancellingDoesNotOverrideManuallySetDriverStatus() {
        // Dyspozytor ustawił odpoczynek ręcznie — anulowanie go nie zmienia
        Driver driver = driver(3L, DriverStatus.RESTING);
        TransportOrder order = existingOrder(10L, OrderStatus.ASSIGNED);
        order.setAssignedDriver(driver);
        when(transportOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        transportOrderService.cancel(10L);

        assertThat(driver.getStatus()).isEqualTo(DriverStatus.RESTING);
        verify(transportOrderRepository, never()).existsByAssignedDriverIdAndStatusInAndIdNot(any(), any(), any());
    }

    @Test
    void rejectsEditingOrderThatIsNotNew() {
        TransportOrder order = existingOrder(10L, OrderStatus.ASSIGNED);
        when(transportOrderRepository.findById(10L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> transportOrderService.update(10L, validRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("order 10 is ASSIGNED");
        // Nie doszło do sprawdzania punktów ani pojazdów
        verify(roadNodeRepository, never()).findById(any());
    }

    private TransportOrder existingOrder(long id, OrderStatus status) {
        TransportOrder order = new TransportOrder();
        order.setId(id);
        order.setLoadingNode(warszawa);
        order.setUnloadingNode(gdansk);
        order.setStatus(status);
        return order;
    }

    private TransportOrderRequest validRequest() {
        LocalDateTime loading = LocalDateTime.now().plusDays(1);
        return request(WARSZAWA_ID, GDANSK_ID, loading, loading.plusDays(1));
    }

    private TransportOrderRequest request(Long loadingId, Long unloadingId,
                                          LocalDateTime loadingDeadline, LocalDateTime deliveryDeadline) {
        return new TransportOrderRequest(loadingId, unloadingId, 18000, 70.0, VehicleType.TILT,
                loadingDeadline, deliveryDeadline, null, null);
    }

    private Driver driver(long id, DriverStatus status) {
        Driver driver = new Driver();
        driver.setId(id);
        driver.setFirstName("Jan");
        driver.setLastName("Kowalski");
        driver.setStatus(status);
        return driver;
    }

    private RoadNode node(long id, String name) {
        RoadNode node = new RoadNode();
        node.setId(id);
        node.setName(name);
        return node;
    }
}
