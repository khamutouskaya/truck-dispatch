package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.OrderRouteEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pełna podróż w symulatorze na prawdziwym grafie: Warszawa → Gdańsk, kierowca 1 (stoi w Warszawie).
 * Konfiguracja testowa (src/test/resources/config/application.yaml): bez korków i bez szumu → rzeczywistość = prognoza.
 */
@SpringBootTest
@Transactional
class SimulationServiceIntegrationTest {

    private static final double STEP_MIN = 10;

    @Autowired private SimulationService simulationService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private TransportOrderRepository transportOrderRepository;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private DriverLocationRepository driverLocationRepository;
    @Autowired private OrderRouteEdgeRepository orderRouteEdgeRepository;
    @Autowired private RoadGraphService roadGraphService;

    // Po DONE samouczenie przeładowuje graf w pamięci; po wycofaniu transakcji testu wracamy do grafu z bazy,
    // żeby „wyuczone” delayFactor nie przeciekły do innych testów w tym samym kontekście Springa
    @AfterTransaction
    void reloadGraphFromCleanDatabase() {
        roadGraphService.reload();
    }

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    private TransportOrder assignedOrder() {
        LocalDateTime now = LocalDateTime.now();
        TransportOrder order = new TransportOrder();
        order.setLoadingNode(node("Warszawa"));
        order.setUnloadingNode(node("Gdańsk"));
        order.setCargoWeightKg(7_000);
        order.setCargoVolumeM3(30);
        order.setRequiredVehicleType(VehicleType.TILT);
        order.setLoadingDeadline(now.plusHours(6));
        order.setDeliveryDeadline(now.plusHours(30));
        order.setEstimatedLoadingMin(60);
        order.setEstimatedUnloadingMin(60);
        transportOrderRepository.saveAndFlush(order);
        assignmentService.assign(order.getId());
        assertThat(order.getAssignedDriver().getId()).isEqualTo(1L);
        return order;
    }

    // Tick, aż zlecenie będzie DONE; zwraca liczbę ticków
    private int runUntilDone(TransportOrder order, Integer forgetAtTick) {
        for (int tick = 1; tick <= 500; tick++) {
            simulationService.advance(STEP_MIN);
            if (forgetAtTick != null && tick == forgetAtTick) {
                simulationService.forgetTimelines(); // jak restart aplikacji
            }
            if (order.getStatus() == OrderStatus.DONE) {
                return tick;
            }
        }
        throw new AssertionError("Order not finished in 500 ticks");
    }

    @Test
    void driverDrivesWholeTripAndBecomesAvailableAtUnloading() {
        TransportOrder order = assignedOrder();
        Driver driver = driverRepository.findById(1L).orElseThrow();
        double plannedDrivingMin = orderRouteEdgeRepository.findByOrderIdOrderBySeq(order.getId()).stream()
                .mapToDouble(OrderRouteEdge::getBaseTimeMin).sum();

        // Tick 1: punkt startowy, jeszcze ASSIGNED; tick 2: kierowca stoi w miejscu załadunku → IN_PROGRESS
        simulationService.advance(STEP_MIN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        simulationService.advance(STEP_MIN);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        runUntilDone(order, null);

        // Kierowca wolny w Gdańsku
        RoadNode gdansk = node("Gdańsk");
        assertThat(driver.getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(driver.getCurrentLat()).isCloseTo(gdansk.getLat(), within(1e-9));
        assertThat(driver.getCurrentLon()).isCloseTo(gdansk.getLon(), within(1e-9));
        assertThat(driver.getLocationSource()).isEqualTo(LocationSource.SIMULATION);

        // Liczniki: ~280 min jazdy (bez korków i szumu = prognoza); > 270 → była jedna przerwa, licznik od przerwy
        assertThat(driver.getDrivingTimeTodayMin()).isEqualTo((int) Math.round(plannedDrivingMin));
        assertThat(driver.getContinuousDrivingMin()).isEqualTo((int) Math.round(plannedDrivingMin - 270));

        // Historia GPS: co 10 min symulacji, źródło SIMULATION, kierowca zmierza na północ
        List<DriverLocation> track = driverLocationRepository.findByOrderIdOrderByRecordedAtAscIdAsc(order.getId());
        assertThat(track).allSatisfy(p -> assertThat(p.getSource()).isEqualTo(LocationSource.SIMULATION));
        for (int i = 1; i < track.size(); i++) {
            assertThat(Duration.between(track.get(i - 1).getRecordedAt(), track.get(i).getRecordedAt()))
                    .isEqualTo(Duration.ofMinutes(10));
        }
        assertThat(track.getFirst().getLat()).isCloseTo(node("Warszawa").getLat(), within(1e-9));
        assertThat(track.getLast().getLat()).isGreaterThan(54.0);
    }

    @Test
    void tripContinuesIdenticallyAfterRestart() {
        // Ta sama podróż dwa razy: raz bez przerwy, raz z „restartem” w połowie — wynik musi być identyczny
        TransportOrder first = assignedOrder();
        int ticksWithoutRestart = runUntilDone(first, null);
        Driver driver = driverRepository.findById(1L).orElseThrow();
        int drivingWithoutRestart = driver.getDrivingTimeTodayMin();

        // Przywracamy stan kierowcy sprzed podróży i wysyłamy go ponownie z Warszawy
        driver.setDrivingTimeTodayMin(0);
        driver.setDrivingTimeWeekMin(1200);
        driver.setContinuousDrivingMin(0);
        driver.setCurrentLat(node("Warszawa").getLat());
        driver.setCurrentLon(node("Warszawa").getLon());
        TransportOrder second = assignedOrder();
        int ticksWithRestart = runUntilDone(second, 20);

        assertThat(ticksWithRestart).isEqualTo(ticksWithoutRestart);
        assertThat(driver.getDrivingTimeTodayMin()).isEqualTo(drivingWithoutRestart);
    }

    @Test
    void ordersWithoutDriverOrFinishedAreIgnored() {
        long before = driverLocationRepository.count();

        simulationService.advance(STEP_MIN); // zlecenia z V3 są NEW — nic do symulowania

        assertThat(driverLocationRepository.count()).isEqualTo(before);
    }
}
