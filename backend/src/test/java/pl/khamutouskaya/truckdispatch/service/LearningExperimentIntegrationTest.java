package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.algorithm.graph.RoadGraph;
import pl.khamutouskaya.truckdispatch.algorithm.graph.Route;
import pl.khamutouskaya.truckdispatch.algorithm.graph.VehicleProfile;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.DriverLocation;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision;
import pl.khamutouskaya.truckdispatch.repository.DriverLocationRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Eksperyment do pracy dyplomowej: jak samouczenie delayFactor poprawia prognozę czasu przyjazdu (ETA).
 * <p>
 * Warunki kontrolowane: 20 razy ta sama podróż Warszawa → Ostróda (S7, jedna krawędź, ~2,8 h — bez przerw),
 * kierowca 1 za każdym razem wypoczęty w Warszawie, załadunek i rozładunek 0 min — w ETA jest tylko jazda.
 * Ukryty korek na S7: × 1,333 (wtedy pierwsza prognoza myli się o (1,333 − 1) / 1,333 = 25%), szum ±10%, seed 42.
 * Szum podróży zależy od (seed, id zlecenia), a id pochodzi z sekwencji bazy — konkretne liczby różnią się
 * między uruchomieniami, tendencja (≈ 25% → poziom szumu) jest stała.
 * <p>
 * Błąd ETA podróży n = |prognoza − fakt| / fakt, gdzie prognoza = koniec z decyzji przydziału − chwila decyzji,
 * fakt = czas między pierwszym a ostatnim punktem GPS (próbka co 1 min → fakt zawyżony o ≤ 1 min, ≤ 0,6%).
 * Wynik: tabela w konsoli i target/experiments/eta-learning.csv (do wykresu).
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "simulator.noise=0.10",
        "simulator.seed=42",
        "simulator.congestion[0].from=Warszawa",
        "simulator.congestion[0].to=Ostróda",
        "simulator.congestion[0].factor=1.3333333333"})
class LearningExperimentIntegrationTest {

    private static final int TRIPS = 20;
    private static final double STEP_MIN = 1;
    private static final double TRUE_FACTOR = 1.3333333333;
    private static final long DRIVER_ID = 1L;

    @Autowired private SimulationService simulationService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private RouteService routeService;
    @Autowired private RoadGraphService roadGraphService;
    @Autowired private TransportOrderRepository transportOrderRepository;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private RoadEdgeRepository roadEdgeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private DriverLocationRepository driverLocationRepository;

    @AfterTransaction
    void reloadGraphFromCleanDatabase() {
        roadGraphService.reload();
    }

    private record TripResult(int trip, double delayFactorBefore, double forecastMin, double actualMin,
                              double errorPct) {
    }

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void etaErrorDropsFromAbout25PercentToNoiseLevel() throws IOException {
        RoadNode warszawa = node("Warszawa");
        RoadNode ostroda = node("Ostróda");
        RoadEdge s7 = roadEdgeRepository.findAll().stream()
                .filter(e -> e.getFromNode().getId().equals(warszawa.getId()) && e.getToNode().getId().equals(ostroda.getId()))
                .findFirst().orElseThrow();
        Driver driver = driverRepository.findById(DRIVER_ID).orElseThrow();

        String routeBefore = describeRoute("Warszawa", "Gdańsk");
        List<TripResult> results = new ArrayList<>();

        for (int trip = 1; trip <= TRIPS; trip++) {
            // Warunki kontrolowane: kierowca wypoczęty, w Warszawie
            driver.setCurrentLat(warszawa.getLat());
            driver.setCurrentLon(warszawa.getLon());
            driver.setDrivingTimeTodayMin(0);
            driver.setDrivingTimeWeekMin(0);
            driver.setContinuousDrivingMin(0);
            double delayFactorBefore = s7.getDelayFactor();

            TransportOrder order = newOrder(warszawa, ostroda);
            AssignmentDecision decision = assignmentService.assign(order.getId());
            assertThat(decision.assignedDriverId()).isEqualTo(DRIVER_ID);
            double forecastMin = minutes(decision.evaluatedAt(), decision.ranking().getFirst().trip().finish());

            for (int tick = 0; tick < 1000 && order.getStatus() != OrderStatus.DONE; tick++) {
                simulationService.advance(STEP_MIN);
            }
            assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);

            List<DriverLocation> track = driverLocationRepository.findByOrderIdOrderByRecordedAtAscIdAsc(order.getId());
            double actualMin = minutes(track.getFirst().getRecordedAt(), track.getLast().getRecordedAt());
            results.add(new TripResult(trip, delayFactorBefore, forecastMin, actualMin,
                    100 * Math.abs(forecastMin - actualMin) / actualMin));
        }

        String routeAfter = describeRoute("Warszawa", "Gdańsk");
        report(results, s7.getDelayFactor(), routeBefore, routeAfter);

        // Szum zależy od id zlecenia (sekwencja w bazie) — liczby różnią się między uruchomieniami, trend nie.
        // Dlatego progi są statystyczne, z zapasem ~3σ, żeby test nie był niestabilny.

        // Pierwsza podróż: system nie zna korka → błąd = 1 − 1 / (1,333 · (1 ± 0,1)) ∈ [16,7%; 31,8%]
        assertThat(results.getFirst().errorPct()).isBetween(15.0, 35.0);
        // Ostatnie 5 podróży: zostaje głównie szum ±10% → średnio ~5% (próg 10% ≈ 3,5σ)
        double first3 = results.subList(0, 3).stream().mapToDouble(TripResult::errorPct).average().orElseThrow();
        double last5 = results.subList(TRIPS - 5, TRIPS).stream().mapToDouble(TripResult::errorPct).average().orElseThrow();
        assertThat(last5).isLessThan(10.0);
        assertThat(last5).isLessThan(first3 / 2);
        // delayFactor blisko prawdziwego korka (wygładzanie α = 0,2 przy szumie ±10% → σ ≈ 0,026)
        assertThat(s7.getDelayFactor()).isCloseTo(TRUE_FACTOR, within(0.08));
    }

    private TransportOrder newOrder(RoadNode from, RoadNode to) {
        LocalDateTime now = LocalDateTime.now();
        TransportOrder order = new TransportOrder();
        order.setLoadingNode(from);
        order.setUnloadingNode(to);
        order.setCargoWeightKg(7_000);
        order.setCargoVolumeM3(30);
        order.setRequiredVehicleType(VehicleType.TILT);
        order.setLoadingDeadline(now.plusHours(6));
        order.setDeliveryDeadline(now.plusHours(30));
        order.setEstimatedLoadingMin(0);
        order.setEstimatedUnloadingMin(0);
        return transportOrderRepository.saveAndFlush(order);
    }

    // Najszybsza trasa dla pustej ciężarówki 4,0 m według bieżącego grafu: węzły i czas
    private String describeRoute(String from, String to) {
        RoadGraph graph = roadGraphService.graph();
        Route route = routeService.routeBetweenNodes(node(from).getId(), node(to).getId(),
                VehicleProfile.empty(4.0, 14_500)).orElseThrow();
        String nodes = route.edges().stream().map(GraphEdge::toId).map(id -> graph.node(id).name())
                .collect(Collectors.joining(" → ", from + " → ", ""));
        return String.format(Locale.ROOT, "%s (%.1f km, %.1f min)", nodes, route.distanceKm(), route.totalTimeMin());
    }

    private static void report(List<TripResult> results, double finalFactor, String routeBefore, String routeAfter)
            throws IOException {
        StringBuilder csv = new StringBuilder("trip,delay_factor_before,forecast_min,actual_min,eta_error_pct\n");
        StringBuilder table = new StringBuilder("\n=== ETA learning experiment: Warszawa → Ostróda, hidden ×1.333, noise ±10% ===\n")
                .append(" trip | delayFactor | forecast min | actual min | ETA error %\n");
        for (TripResult r : results) {
            csv.append(String.format(Locale.ROOT, "%d,%.4f,%.1f,%.1f,%.2f%n",
                    r.trip(), r.delayFactorBefore(), r.forecastMin(), r.actualMin(), r.errorPct()));
            table.append(String.format(Locale.ROOT, " %4d | %11.4f | %12.1f | %10.1f | %10.1f%n",
                    r.trip(), r.delayFactorBefore(), r.forecastMin(), r.actualMin(), r.errorPct()));
        }
        table.append(String.format(Locale.ROOT, "delayFactor after %d trips: %.4f (true 1.3333)%n", results.size(), finalFactor))
                .append("Route Warszawa → Gdańsk before: ").append(routeBefore).append('\n')
                .append("Route Warszawa → Gdańsk after:  ").append(routeAfter).append('\n');
        System.out.println(table);

        Path dir = Path.of("target", "experiments");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("eta-learning.csv"), csv, StandardCharsets.UTF_8);
    }

    private static double minutes(LocalDateTime from, LocalDateTime to) {
        return Duration.between(from, to).toMillis() / 60_000.0;
    }
}
