package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.graph.GraphEdge;
import pl.khamutouskaya.truckdispatch.domain.OrderRouteEdge;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadEdge;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.repository.OrderRouteEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadEdgeRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Cała pętla samouczenia na prawdziwym grafie: przydział → symulacja z ukrytym korkiem → GPS → nowy delayFactor.
 * Ukryty korek tylko na Warszawa → Ostróda (S7) × 1,25; bez szumu. Algorytm o nim nie wie.
 * Oczekiwanie po jednym przejeździe: 0,8 · 1,0 + 0,2 · 1,25 = 1,05; na pozostałych krawędziach ≈ 1,0.
 */
@SpringBootTest
@Transactional
@TestPropertySource(properties = {
        "simulator.congestion[0].from=Warszawa",
        "simulator.congestion[0].to=Ostróda",
        "simulator.congestion[0].factor=1.25"})
class LearningServiceIntegrationTest {

    // Próbka GPS co 1 min symulacji (jak przy speedup 60 i ticku 1 s)
    private static final double STEP_MIN = 1;
    // Błąd próbkowania: postój zaczęty między próbkami ≤ 1 min na krawędzi ~100–200 min → < 0,003 w delayFactor
    private static final double TOLERANCE = 0.003;

    @Autowired private SimulationService simulationService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private RoadGraphService roadGraphService;
    @Autowired private TransportOrderRepository transportOrderRepository;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private RoadEdgeRepository roadEdgeRepository;
    @Autowired private OrderRouteEdgeRepository orderRouteEdgeRepository;

    @AfterTransaction
    void reloadGraphFromCleanDatabase() {
        roadGraphService.reload();
    }

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void oneTripTeachesHiddenCongestionOnlyWhereItIs() {
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

        List<OrderRouteEdge> route = orderRouteEdgeRepository.findByOrderIdOrderBySeq(order.getId());
        RoadEdge congested = route.stream().map(OrderRouteEdge::getEdge)
                .filter(e -> e.getFromNode().getName().equals("Warszawa") && e.getToNode().getName().equals("Ostróda"))
                .findFirst().orElseThrow(() -> new AssertionError("Route Warszawa → Gdańsk should use S7 via Ostróda"));

        for (int tick = 0; tick < 2000 && order.getStatus() != OrderStatus.DONE; tick++) {
            simulationService.advance(STEP_MIN);
        }
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DONE);

        // Krawędź z korkiem: 1,0 → 1,05
        assertThat(congested.getDelayFactor()).isCloseTo(1.05, within(TOLERANCE));
        // Pozostałe krawędzie trasy: bez korka → zostają ≈ 1,0
        route.stream().map(OrderRouteEdge::getEdge).filter(e -> !e.getId().equals(congested.getId()))
                .forEach(e -> assertThat(e.getDelayFactor()).as(e.getFromNode().getName() + " → " + e.getToNode().getName())
                        .isCloseTo(1.0, within(TOLERANCE)));
        // Kierunek przeciwny (Ostróda → Warszawa) nie był jechany — bez zmian
        RoadEdge reverse = roadEdgeRepository.findAll().stream()
                .filter(e -> e.getFromNode().getName().equals("Ostróda") && e.getToNode().getName().equals("Warszawa"))
                .findFirst().orElseThrow();
        assertThat(reverse.getDelayFactor()).isEqualTo(1.0);

        // Graf w pamięci przeładowany — następne trasy już liczą z nowym delayFactor
        GraphEdge inMemory = roadGraphService.graph().outgoingEdges(congested.getFromNode().getId()).stream()
                .filter(e -> e.id() == congested.getId()).findFirst().orElseThrow();
        assertThat(inMemory.delayFactor()).isEqualTo(congested.getDelayFactor());
    }
}
