package pl.khamutouskaya.truckdispatch.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.RejectionReason;
import pl.khamutouskaya.truckdispatch.domain.AssignmentResult;
import pl.khamutouskaya.truckdispatch.domain.DriverStatus;
import pl.khamutouskaya.truckdispatch.domain.OrderStatus;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.RankedCandidate;
import pl.khamutouskaya.truckdispatch.dto.AssignmentDecision.RejectedDriver;
import pl.khamutouskaya.truckdispatch.exception.ConflictException;
import pl.khamutouskaya.truckdispatch.exception.NotFoundException;
import pl.khamutouskaya.truckdispatch.repository.AssignmentResultRepository;
import pl.khamutouskaya.truckdispatch.repository.DriverRepository;
import pl.khamutouskaya.truckdispatch.repository.RoadNodeRepository;
import pl.khamutouskaya.truckdispatch.repository.TransportOrderRepository;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Cały algorytm na prawdziwych danych (graf V5, kierowcy i pojazdy V3/V6). @Transactional — zmiany są wycofywane.
 * <p>
 * Zlecenie: Warszawa → Gdańsk, plandeka (TILT), 7 t, załadunek za 6 h, dostawa za 30 h.
 * Kierowcy AVAILABLE z plandeką: 1 (Warszawa, 24 t), 6 (Gdańsk, dziś już 5 h jazdy), 8 (Lublin, tydzień 51 h 40 min).
 */
@SpringBootTest
@Transactional
class AssignmentServiceIntegrationTest {

    @Autowired private AssignmentService assignmentService;
    @Autowired private TransportOrderRepository transportOrderRepository;
    @Autowired private RoadNodeRepository roadNodeRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private AssignmentResultRepository assignmentResultRepository;
    @Autowired private JsonMapper jsonMapper;

    private RoadNode node(String name) {
        return roadNodeRepository.findAll().stream().filter(n -> n.getName().equals(name)).findFirst().orElseThrow();
    }

    private TransportOrder newOrder(int loadingInHours) {
        LocalDateTime now = LocalDateTime.now();
        TransportOrder order = new TransportOrder();
        order.setLoadingNode(node("Warszawa"));
        order.setUnloadingNode(node("Gdańsk"));
        order.setCargoWeightKg(7_000);
        order.setCargoVolumeM3(30);
        order.setRequiredVehicleType(VehicleType.TILT);
        order.setLoadingDeadline(now.plusHours(loadingInHours));
        order.setDeliveryDeadline(now.plusHours(30));
        order.setEstimatedLoadingMin(60);
        order.setEstimatedUnloadingMin(60);
        return transportOrderRepository.saveAndFlush(order);
    }

    private static Map<Long, RejectionReason> reasons(AssignmentDecision decision) {
        return decision.rejected().stream().collect(Collectors.toMap(RejectedDriver::driverId, RejectedDriver::reason));
    }

    @Test
    void assignsDriverFromWarsawAndExplainsEveryRejection() {
        TransportOrder order = newOrder(6);

        AssignmentDecision decision = assignmentService.assign(order.getId());

        // Zwycięzca: kierowca 1 stoi w Warszawie
        assertThat(decision.assignedDriverId()).isEqualTo(1L);
        RankedCandidate winner = decision.ranking().getFirst();
        assertThat(winner.rank()).isEqualTo(1);
        assertThat(winner.assigned()).isTrue();
        assertThat(winner.trip().arrivalAtLoading()).isBefore(order.getLoadingDeadline());
        assertThat(winner.values().get("vehicleUtilization").raw()).isCloseTo(7_000 / 24_000.0, within(1e-9));
        // Suma wkładów = score (dla React: słupek „z czego składa się ocena”)
        double sumOfWeighted = winner.values().values().stream().mapToDouble(v -> v.weighted()).sum();
        assertThat(sumOfWeighted).isCloseTo(winner.score(), within(1e-9));

        // Każdy kierowca AVAILABLE jest albo w rankingu, albo wśród odrzuconych — nikt nie znika bez wyjaśnienia
        long available = driverRepository.findByStatus(DriverStatus.AVAILABLE).size()
                + 1; // zwycięzca ma już ON_ROUTE
        assertThat(decision.ranking().size() + decision.rejected().size()).isEqualTo((int) available);

        Map<Long, RejectionReason> reasons = reasons(decision);
        assertThat(reasons.get(6L)).isEqualTo(RejectionReason.LATE_LOADING);  // Gdańsk: limit dzienny → odpoczynek 11 h
        assertThat(reasons.get(8L)).isEqualTo(RejectionReason.WEEKLY_LIMIT);  // Lublin: zabraknie tygodniowych 56 h
        assertThat(reasons.get(2L)).isEqualTo(RejectionReason.TYPE);          // chłodnia
        assertThat(reasons).doesNotContainKeys(9L, 10L, 11L, 12L);             // ON_ROUTE, RESTING, OFF_DUTY — nie kandydaci

        // Zmiany w bazie: jedna transakcja
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getAssignedDriver().getId()).isEqualTo(1L);
        assertThat(driverRepository.findById(1L).orElseThrow().getStatus()).isEqualTo(DriverStatus.ON_ROUTE);

        List<AssignmentResult> history = assignmentResultRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getScore()).isEqualTo(winner.score());
        // JSON w bazie odczytuje się z powrotem do tej samej struktury
        AssignmentDecision fromDb = jsonMapper.readValue(history.getFirst().getDetails(), AssignmentDecision.class);
        assertThat(fromDb.assignedDriverId()).isEqualTo(1L);
        assertThat(fromDb.criteria()).extracting(AssignmentDecision.CriterionInfo::name).containsExactly(
                "distanceToLoadingKm", "timeToLoadingH", "remainingDrivingTodayH", "stopCount", "vehicleUtilization");
    }

    @Test
    void orderStaysNewWhenNobodyCanMakeIt() {
        // Termin załadunku minął godzinę temu — nikt nie zdąży, ale decyzja z powodami i tak jest zapisana
        TransportOrder order = newOrder(-1);

        AssignmentDecision decision = assignmentService.assign(order.getId());

        assertThat(decision.assignedDriverId()).isNull();
        assertThat(decision.ranking()).isEmpty();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(reasons(decision).get(1L)).isEqualTo(RejectionReason.LATE_LOADING);

        AssignmentResult saved = assignmentResultRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId()).getFirst();
        assertThat(saved.getDriver()).isNull();
        assertThat(saved.getScore()).isNull();
    }

    @Test
    void evaluateDoesNotChangeAnything() {
        TransportOrder order = newOrder(6);

        AssignmentDecision preview = assignmentService.evaluate(order.getId());

        assertThat(preview.assignedDriverId()).isEqualTo(1L);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(driverRepository.findById(1L).orElseThrow().getStatus()).isEqualTo(DriverStatus.AVAILABLE);
        assertThat(assignmentResultRepository.findByOrderIdOrderByCreatedAtDescIdDesc(order.getId())).isEmpty();
    }

    @Test
    void assignedOrderCannotBeAssignedAgain() {
        TransportOrder order = newOrder(6);
        assignmentService.assign(order.getId());

        assertThatThrownBy(() -> assignmentService.assign(order.getId()))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("ASSIGNED");
    }

    @Test
    void unknownOrderGives404() {
        assertThatThrownBy(() -> assignmentService.assign(999_999L)).isInstanceOf(NotFoundException.class);
    }
}
