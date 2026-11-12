package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.Activity;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.TrafficModel;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.TripState;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.TripTimeline;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;
import pl.khamutouskaya.truckdispatch.config.SimulatorProperties;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.LocationSource;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Symulator ruchu: przesuwa kierowców zleceń ASSIGNED / IN_PROGRESS po zaplanowanych trasach.
 * <p>
 * Czas symulacji jest liczony osobno dla każdego zlecenia z historii GPS:
 * t = (czas ostatniego punktu − czas pierwszego punktu) + krok. Dzięki temu:
 * <ul>
 *   <li>przyspieszenie (speedup) nie zniekształca czasów — w historii są minuty symulacji;</li>
 *   <li>po restarcie aplikacji podróż jest kontynuowana: start (miejsce, chwila), trasa, liczniki kierowcy
 *       i ziarno szumu są w bazie, więc oś czasu odtwarza się identycznie.</li>
 * </ul>
 * Uproszczenie: znaczniki czasu w historii to czas symulacji, który przy speedup &gt; 1 wyprzedza zegar ścienny.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private static final Set<OrderStatus> ACTIVE = Set.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);

    private final TransportOrderRepository transportOrderRepository;
    private final PlannedRouteLoader plannedRouteLoader;
    private final LearningService learningService;
    private final DriverLocationRepository driverLocationRepository;
    private final RoadGraphService roadGraphService;
    private final SimulatorProperties properties;
    private final Clock clock;

    // Pamięć podręczna osi czasu (odtwarzalna z bazy) — żeby nie budować jej co tick
    private final Map<Long, TripTimeline> timelineByOrderId = new ConcurrentHashMap<>();

    /**
     * Jeden krok symulacji dla wszystkich aktywnych zleceń.
     *
     * @param stepMin o ile minut symulacji przesunąć każdą podróż
     */
    @Transactional
    public void advance(double stepMin) {
        for (TransportOrder order : transportOrderRepository.findByStatusIn(ACTIVE, Sort.by("id"))) {
            if (order.getAssignedDriver() != null) {
                advanceOrder(order, stepMin);
            }
        }
    }

    private void advanceOrder(TransportOrder order, double stepMin) {
        Driver driver = order.getAssignedDriver();
        var first = driverLocationRepository.findFirstByOrderIdOrderByRecordedAtAscIdAsc(order.getId());

        // Pierwszy tick zlecenia: punkt startowy = obecne położenie kierowcy, t = 0
        if (first.isEmpty()) {
            LocalDateTime start = LocalDateTime.now(clock);
            GeoPoint position = new GeoPoint(driver.getCurrentLat(), driver.getCurrentLon());
            recordLocation(driver, order, position, start);
            log.info("Simulation: order {} started by driver {}", order.getId(), driver.getId());
            return;
        }

        LocalDateTime start = first.get().getRecordedAt();
        LocalDateTime last = driverLocationRepository.findFirstByOrderIdOrderByRecordedAtDescIdDesc(order.getId())
                .orElseThrow().getRecordedAt();
        double tMin = minutesBetween(start, last) + stepMin;

        TripTimeline timeline = timelineByOrderId.computeIfAbsent(order.getId(),
                id -> buildTimeline(order, driver, new GeoPoint(first.get().getLat(), first.get().getLon())));
        TripState state = timeline.stateAt(tMin);
        LocalDateTime now = start.plusSeconds(Math.round(tMin * 60));
        recordLocation(driver, order, state.position(), now);

        if (order.getStatus() == OrderStatus.ASSIGNED && tMin >= timeline.loadingStartMin()) {
            order.setStatus(OrderStatus.IN_PROGRESS);
            log.info("Simulation: order {} loading started", order.getId());
        }
        if (state.activity() == Activity.FINISHED) {
            finish(order, driver, timeline);
        }
    }

    // Koniec rozładunku: zlecenie DONE, kierowca wolny w miejscu rozładunku z licznikami po podróży
    private void finish(TransportOrder order, Driver driver, TripTimeline timeline) {
        order.setStatus(OrderStatus.DONE);
        driver.setStatus(DriverStatus.AVAILABLE);
        DriverWorkState end = timeline.endState();
        driver.setDrivingTimeTodayMin((int) Math.round(end.drivingTodayMin()));
        driver.setDrivingTimeWeekMin((int) Math.round(end.drivingWeekMin()));
        driver.setContinuousDrivingMin((int) Math.round(end.continuousDrivingMin()));
        timelineByOrderId.remove(order.getId());
        log.info("Simulation: order {} DONE, driver {} available (breaks {}, rests {})",
                order.getId(), driver.getId(), timeline.breakCount(), timeline.restCount());

        // Samouczenie: rzeczywiste czasy krawędzi z GPS → nowe delayFactor
        learningService.learnFromOrder(order.getId());
    }

    // Punkt GPS do historii + bieżące położenie kierowcy
    private void recordLocation(Driver driver, TransportOrder order, GeoPoint position, LocalDateTime at) {
        DriverLocation location = new DriverLocation();
        location.setDriver(driver);
        location.setOrder(order);
        location.setLat(position.lat());
        location.setLon(position.lon());
        location.setRecordedAt(at);
        location.setSource(LocationSource.SIMULATION);
        driverLocationRepository.save(location);

        driver.setCurrentLat(position.lat());
        driver.setCurrentLon(position.lon());
        driver.setLocationSource(LocationSource.SIMULATION);
        driver.setLocationUpdatedAt(at);
    }

    // --- Budowa osi czasu z danych w bazie ---

    private TripTimeline buildTimeline(TransportOrder order, Driver driver, GeoPoint start) {
        PlannedRouteLoader.PlannedRoute route = plannedRouteLoader.load(order.getId());
        // Liczniki kierowcy w bazie zmieniają się dopiero po zakończeniu — więc to stan ze startu podróży
        DriverWorkState state = new DriverWorkState(driver.getDrivingTimeTodayMin(), driver.getDrivingTimeWeekMin(),
                driver.getContinuousDrivingMin());
        // Ziarno zależne od zlecenia: ta sama podróż po restarcie ma ten sam szum
        TrafficModel traffic = new TrafficModel(congestionByEdgeId(), properties.noise(),
                TrafficModel.randomFor(properties.seed(), order.getId()));

        RoadNode loading = order.getLoadingNode();
        RoadNode unloading = order.getUnloadingNode();
        return TripTimeline.build(start,
                route.empty(), PlannedRouteLoader.point(loading), handlingMin(order.getEstimatedLoadingMin(), loading),
                route.loaded(), PlannedRouteLoader.point(unloading), handlingMin(order.getEstimatedUnloadingMin(), unloading),
                state, traffic);
    }

    // Korki z konfiguracji (nazwy węzłów) → id krawędzi grafu
    private Map<Long, Double> congestionByEdgeId() {
        RoadGraph graph = roadGraphService.graph();
        Map<Long, Double> result = new HashMap<>();
        for (SimulatorProperties.Congestion c : properties.congestion()) {
            GraphEdge edge = graph.nodes().stream()
                    .filter(n -> n.name().equals(c.from()))
                    .flatMap(n -> graph.outgoingEdges(n.id()).stream())
                    .filter(e -> graph.node(e.toId()).name().equals(c.to()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "simulator.congestion: no road " + c.from() + " → " + c.to()));
            result.put(edge.id(), c.factor());
        }
        return result;
    }

    private static int handlingMin(Integer estimated, RoadNode node) {
        return estimated != null ? estimated : node.getDefaultHandlingMin();
    }

    private static double minutesBetween(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMillis() / 60_000.0;
    }

    // Dla testów: symulacja „restartu” — oś czasu zostanie odtworzona z bazy
    void forgetTimelines() {
        timelineByOrderId.clear();
    }
}
