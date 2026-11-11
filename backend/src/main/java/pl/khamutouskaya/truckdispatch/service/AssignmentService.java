package pl.khamutouskaya.truckdispatch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.CandidateFilter;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.CandidateScore;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.CandidateScorer;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.RejectionReason;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.WeightedCriterion;
import pl.khamutouskaya.truckdispatch.algorithm.graph.EdgeTimeCalculator;
import pl.khamutouskaya.truckdispatch.algorithm.graph.FirstMile;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GeoDistance;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphNode;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.TripPlan;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulator;
import pl.khamutouskaya.truckdispatch.config.AssignmentWeightsProperties;
import pl.khamutouskaya.truckdispatch.domain.AssignmentResult;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RouteLeg;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.CriterionInfo;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.CriterionValue;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.RankedCandidate;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.RejectedDriver;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.TripSummary;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.AssignmentResultRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.OrderRouteEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import pl.khamutouskaya.truckdispatch.repository.VehicleRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Algorytm przydziału zlecenia (docs/karta-i-algorytm.md, część 3) — składa elementy z pakietu algorithm:
 * kandydaci AVAILABLE → szybki odsiew → trasy (Dijkstra) → symulacja czasu pracy → filtr terminów →
 * ocena wielokryterialna → zapis decyzji.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssignmentService {

    private static final Sort BY_ID = Sort.by("id");

    private final TransportOrderRepository transportOrderRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final AssignmentResultRepository assignmentResultRepository;
    private final OrderRouteEdgeRepository orderRouteEdgeRepository;
    private final RoadEdgeRepository roadEdgeRepository;
    private final RoadGraphService roadGraphService;
    private final RouteService routeService;
    private final AssignmentWeightsProperties weights;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /**
     * Podgląd: ranking i powody odrzucenia bez zmiany danych (nic nie jest zapisywane).
     */
    public AssignmentDecision evaluate(Long orderId) {
        return evaluate(findNewOrder(orderId), LocalDateTime.now(clock)).decision();
    }

    /**
     * Przydział w jednej transakcji: zlecenie → ASSIGNED, kierowca → ON_ROUTE, zapis AssignmentResult
     * i zaplanowanej trasy zwycięzcy (order_route_edge — dla symulatora i samouczenia).
     * Gdy nikt nie przeszedł filtrów, zlecenie zostaje NEW, a decyzja z powodami i tak jest zapisywana.
     */
    @Transactional
    public AssignmentDecision assign(Long orderId) {
        TransportOrder order = findNewOrder(orderId);
        Evaluation evaluation = evaluate(order, LocalDateTime.now(clock));
        AssignmentDecision decision = evaluation.decision();

        AssignmentResult result = new AssignmentResult();
        result.setOrder(order);
        result.setCreatedAt(decision.evaluatedAt());
        if (decision.assignedDriverId() != null) {
            Driver driver = driverRepository.findById(decision.assignedDriverId())
                    .orElseThrow(() -> new NotFoundException("Driver", decision.assignedDriverId()));
            order.setStatus(OrderStatus.ASSIGNED);
            order.setAssignedDriver(driver);
            driver.setStatus(DriverStatus.ON_ROUTE);
            result.setDriver(driver);
            result.setScore(decision.ranking().getFirst().score());
            savePlannedRoute(order, evaluation.detailsByDriverId().get(driver.getId()));
        }
        result.setDetails(jsonMapper.writeValueAsString(decision));
        assignmentResultRepository.save(result);
        return decision;
    }

    /**
     * Ostatnia zapisana decyzja dla zlecenia (w dowolnym statusie) — do ekranu „Szczegóły zlecenia”.
     */
    public AssignmentDecision latestDecision(Long orderId) {
        if (!transportOrderRepository.existsById(orderId)) {
            throw new NotFoundException("Order", orderId);
        }
        AssignmentResult latest = assignmentResultRepository.findByOrderIdOrderByCreatedAtDescIdDesc(orderId)
                .stream().findFirst()
                .orElseThrow(() -> new NotFoundException("No assignment decision for order " + orderId));
        return jsonMapper.readValue(latest.getDetails(), AssignmentDecision.class);
    }

    // --- Obliczenie decyzji ---

    // Decyzja + szczegóły kandydatów (trasy), których nie ma w JSON, a są potrzebne do zapisu trasy zwycięzcy
    private record Evaluation(AssignmentDecision decision, Map<Long, CandidateDetails> detailsByDriverId) {
    }

    private Evaluation evaluate(TransportOrder order, LocalDateTime now) {
        List<WeightedCriterion> criteria = weights.toCriteria();
        Map<Long, Vehicle> vehicleByDriverId = vehiclesByDriverId();

        List<Candidate> candidates = new ArrayList<>();
        Map<Long, CandidateDetails> detailsByDriverId = new HashMap<>();
        List<RejectedDriver> rejected = new ArrayList<>();

        for (Driver driver : driverRepository.findByStatus(DriverStatus.AVAILABLE, BY_ID)) {
            Vehicle vehicle = vehicleByDriverId.get(driver.getId());
            Optional<RejectionReason> quickRejection = CandidateFilter.quickCheck(driver, vehicle, order);
            if (quickRejection.isPresent()) {
                rejected.add(rejectedDriver(driver, vehicle, quickRejection.get()));
                continue;
            }
            CandidateEvaluation evaluation = evaluateCandidate(driver, vehicle, order, now);
            if (evaluation.rejection() != null) {
                rejected.add(rejectedDriver(driver, vehicle, evaluation.rejection()));
            } else {
                candidates.add(evaluation.candidate());
                detailsByDriverId.put(driver.getId(), evaluation.details());
            }
        }

        List<CandidateScore> scores = CandidateScorer.rank(candidates, order, criteria);
        List<RankedCandidate> ranking = new ArrayList<>(scores.size());
        for (int i = 0; i < scores.size(); i++) {
            CandidateScore score = scores.get(i);
            long driverId = score.candidate().driverId();
            ranking.add(rankedCandidate(i + 1, score, criteria, detailsByDriverId.get(driverId)));
        }

        Long assignedDriverId = ranking.isEmpty() ? null : ranking.getFirst().driverId();
        List<CriterionInfo> criterionInfos = criteria.stream()
                .map(wc -> new CriterionInfo(wc.criterion().name(), wc.weight(), wc.criterion().higherIsBetter()))
                .toList();
        return new Evaluation(
                new AssignmentDecision(order.getId(), now, assignedDriverId, criterionInfos, ranking, rejected),
                detailsByDriverId);
    }

    // Wynik dla jednego kierowcy po szybkim odsiewie: albo kandydat, albo powód odrzucenia
    private record CandidateEvaluation(Candidate candidate, CandidateDetails details, RejectionReason rejection) {
        static CandidateEvaluation rejectedFor(RejectionReason reason) {
            return new CandidateEvaluation(null, null, reason);
        }
    }

    // Dane, które nie wchodzą do oceny: do wyjaśnienia (trip) i do zapisu trasy zwycięzcy
    private record CandidateDetails(String vehicleRegistration, TripSummary trip,
                                    Route toLoading, VehicleProfile emptyProfile,
                                    Route toUnloading, VehicleProfile loadedProfile) {
    }

    private CandidateEvaluation evaluateCandidate(Driver driver, Vehicle vehicle, TransportOrder order,
                                                  LocalDateTime now) {
        long loadingNodeId = order.getLoadingNode().getId();
        long unloadingNodeId = order.getUnloadingNode().getId();
        double lat = driver.getCurrentLat();
        double lon = driver.getCurrentLon();

        // Krok 4: dwie trasy — pustym do załadunku, z ładunkiem do rozładunku (inne ograniczenia i prędkości)
        VehicleProfile empty = VehicleProfile.empty(vehicle.getHeightM(), vehicle.getEmptyWeightKg());
        VehicleProfile loaded = VehicleProfile.loaded(vehicle.getHeightM(), vehicle.getEmptyWeightKg(),
                order.getCargoWeightKg());
        Optional<Route> toLoading = routeService.routeFromPosition(lat, lon, loadingNodeId, empty);
        Optional<Route> toUnloading = routeService.routeBetweenNodes(loadingNodeId, unloadingNodeId, loaded);
        if (toLoading.isEmpty() || toUnloading.isEmpty()) {
            return CandidateEvaluation.rejectedFor(RejectionReason.NO_ROUTE);
        }

        // Krok 5: symulacja czasu pracy
        TripPlan plan = new TripPlan(toLoading.get().totalTimeMin(), loadingMin(order),
                toUnloading.get().totalTimeMin(), unloadingMin(order));
        DriverWorkState state = new DriverWorkState(driver.getDrivingTimeTodayMin(), driver.getDrivingTimeWeekMin(),
                driver.getContinuousDrivingMin());
        WorkTimeSimulation simulation = WorkTimeSimulator.simulate(now, state, plan);

        // Krok 6: filtr twardy
        Optional<RejectionReason> lateRejection = CandidateFilter.deadlineCheck(simulation, order);
        if (lateRejection.isPresent()) {
            return CandidateEvaluation.rejectedFor(lateRejection.get());
        }

        // K1 = wszystkie puste kilometry: pierwsza mila (prosta × 1,3) + trasa po grafie
        GraphNode startNode = roadGraphService.graph().node(toLoading.get().startNodeId());
        double firstMileKm = FirstMile.roadKm(GeoDistance.haversineKm(lat, lon, startNode.lat(), startNode.lon()));
        double distanceToLoadingKm = firstMileKm + toLoading.get().distanceKm();

        Candidate candidate = new Candidate(driver.getId(), fullName(driver), vehicle.getCapacityKg(),
                distanceToLoadingKm, simulation);
        TripSummary trip = new TripSummary(distanceToLoadingKm, firstMileKm, toUnloading.get().distanceKm(),
                plan.emptyDrivingMin(), plan.loadedDrivingMin(), simulation.arrivalAtLoading(), simulation.finish(),
                simulation.breakCount(), simulation.restCount(), simulation.endState().remainingDrivingTodayMin());
        CandidateDetails details = new CandidateDetails(vehicle.getRegistrationNumber(), trip,
                toLoading.get(), empty, toUnloading.get(), loaded);
        return new CandidateEvaluation(candidate, details, null);
    }

    private static RankedCandidate rankedCandidate(int rank, CandidateScore score, List<WeightedCriterion> criteria,
                                                   CandidateDetails details) {
        // Kolejność kluczy jak w criteria — Jackson zapisze mapę w tej kolejności
        Map<String, CriterionValue> values = new LinkedHashMap<>();
        for (WeightedCriterion wc : criteria) {
            String name = wc.criterion().name();
            double normalized = score.normalizedValues().get(name);
            values.put(name, new CriterionValue(score.rawValues().get(name), normalized, wc.weight() * normalized));
        }
        Candidate c = score.candidate();
        return new RankedCandidate(rank, c.driverId(), c.driverName(), details.vehicleRegistration(), score.score(),
                rank == 1, values, details.trip());
    }

    // Trasa zwycięzcy krawędź po krawędzi: prognoza z delayFactor i czas bazowy (bez niego), dla pojazdu pustego/z ładunkiem
    private void savePlannedRoute(TransportOrder order, CandidateDetails details) {
        int seq = 0;
        for (GraphEdge edge : details.toLoading().edges()) {
            orderRouteEdgeRepository.save(routeEdge(order, seq++, RouteLeg.EMPTY, edge, details.emptyProfile()));
        }
        for (GraphEdge edge : details.toUnloading().edges()) {
            orderRouteEdgeRepository.save(routeEdge(order, seq++, RouteLeg.LOADED, edge, details.loadedProfile()));
        }
    }

    private OrderRouteEdge routeEdge(TransportOrder order, int seq, RouteLeg leg, GraphEdge edge,
                                     VehicleProfile vehicle) {
        OrderRouteEdge routeEdge = new OrderRouteEdge();
        routeEdge.setOrder(order);
        routeEdge.setSeq(seq);
        routeEdge.setLeg(leg);
        routeEdge.setEdge(roadEdgeRepository.getReferenceById(edge.id())); // bez zapytania SELECT — tylko klucz obcy
        routeEdge.setPlannedTimeMin(EdgeTimeCalculator.travelTimeMin(edge, vehicle));
        routeEdge.setBaseTimeMin(EdgeTimeCalculator.baseTravelTimeMin(edge, vehicle));
        return routeEdge;
    }

    // --- Pomocnicze ---

    private TransportOrder findNewOrder(Long orderId) {
        TransportOrder order = transportOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Order", orderId));
        if (order.getStatus() != OrderStatus.NEW) {
            throw new ConflictException("Only orders in status NEW can be assigned; order " + orderId
                    + " is " + order.getStatus());
        }
        return order;
    }

    // Jedno zapytanie zamiast osobnego dla każdego kierowcy (N+1)
    private Map<Long, Vehicle> vehiclesByDriverId() {
        Map<Long, Vehicle> result = new HashMap<>();
        for (Vehicle vehicle : vehicleRepository.findAll(BY_ID)) {
            if (vehicle.getDriver() != null) {
                result.put(vehicle.getDriver().getId(), vehicle);
            }
        }
        return result;
    }

    // Czas załadunku: podany w zleceniu albo domyślny dla węzła
    private static int loadingMin(TransportOrder order) {
        Integer estimated = order.getEstimatedLoadingMin();
        return estimated != null ? estimated : order.getLoadingNode().getDefaultHandlingMin();
    }

    private static int unloadingMin(TransportOrder order) {
        Integer estimated = order.getEstimatedUnloadingMin();
        return estimated != null ? estimated : order.getUnloadingNode().getDefaultHandlingMin();
    }

    private static RejectedDriver rejectedDriver(Driver driver, Vehicle vehicle, RejectionReason reason) {
        return new RejectedDriver(driver.getId(), fullName(driver),
                vehicle != null ? vehicle.getRegistrationNumber() : null, reason);
    }

    private static String fullName(Driver driver) {
        return driver.getFirstName() + " " + driver.getLastName();
    }
}
