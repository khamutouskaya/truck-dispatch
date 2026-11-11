package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.TripPlan;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulator;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;
import pl.khamutouskaya.truckdispatch.domain.VehicleType;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Przykład z docs/karta-i-algorytm.md, część 4: zlecenie Łódź → Kraków, 18 t, plandeka (TILT),
 * termin załadunku 12:00, termin dostawy 20:00, start 08:00.
 */
class CandidateFilterTest {

    private static final LocalDateTime EIGHT = LocalDateTime.of(2026, 11, 11, 8, 0);

    private static final double LODZ_LAT = 51.7592, LODZ_LON = 19.4560;
    private static final double WARSZAWA_LAT = 52.2297, WARSZAWA_LON = 21.0122;

    private static TransportOrder order() {
        RoadNode lodz = new RoadNode();
        lodz.setName("Łódź");
        lodz.setLat(LODZ_LAT);
        lodz.setLon(LODZ_LON);

        TransportOrder order = new TransportOrder();
        order.setLoadingNode(lodz);
        order.setCargoWeightKg(18_000);
        order.setCargoVolumeM3(60);
        order.setRequiredVehicleType(VehicleType.TILT);
        order.setLoadingDeadline(EIGHT.withHour(12));
        order.setDeliveryDeadline(EIGHT.withHour(20));
        return order;
    }

    private static Driver driverAt(Double lat, Double lon) {
        Driver driver = new Driver();
        driver.setCurrentLat(lat);
        driver.setCurrentLon(lon);
        return driver;
    }

    private static Vehicle vehicle(VehicleType type, int capacityKg, double volumeM3) {
        Vehicle vehicle = new Vehicle();
        vehicle.setType(type);
        vehicle.setCapacityKg(capacityKg);
        vehicle.setVolumeM3(volumeM3);
        return vehicle;
    }

    private static final Vehicle TILT_24T = vehicle(VehicleType.TILT, 24_000, 90);

    // --- Szybki odsiew ---

    @Test
    void driverAFromWarsawPassesQuickCheck() {
        assertThat(CandidateFilter.quickCheck(driverAt(WARSZAWA_LAT, WARSZAWA_LON), TILT_24T, order())).isEmpty();
        // Warszawa — Łódź w linii prostej ≈ 119 km
        assertThat(CandidateFilter.straightDistanceToLoadingKm(driverAt(WARSZAWA_LAT, WARSZAWA_LON), order()))
                .isCloseTo(119, within(1.0));
    }

    @Test
    void driverCStandingInLodzIsRejectedForRefrigerator() {
        // Stoi dokładnie w miejscu załadunku, ale ma chłodnię zamiast plandeki
        Vehicle refrigerator = vehicle(VehicleType.REFRIGERATOR, 24_000, 90);

        assertThat(CandidateFilter.quickCheck(driverAt(LODZ_LAT, LODZ_LON), refrigerator, order()))
                .contains(RejectionReason.TYPE);
    }

    @Test
    void tooSmallCapacityOrVolumeIsRejected() {
        Driver driver = driverAt(WARSZAWA_LAT, WARSZAWA_LON);

        assertThat(CandidateFilter.quickCheck(driver, vehicle(VehicleType.TILT, 12_000, 90), order()))
                .contains(RejectionReason.CAPACITY);
        assertThat(CandidateFilter.quickCheck(driver, vehicle(VehicleType.TILT, 24_000, 40), order()))
                .contains(RejectionReason.CAPACITY);
        // Dokładnie tyle, ile ładunku — mieści się
        assertThat(CandidateFilter.quickCheck(driver, vehicle(VehicleType.TILT, 18_000, 60), order())).isEmpty();
    }

    @Test
    void driverFurtherThan800KmIsRejected() {
        // Okolice Sankt Petersburga, ≈ 1200 km od Łodzi
        assertThat(CandidateFilter.quickCheck(driverAt(59.9, 30.3), TILT_24T, order()))
                .contains(RejectionReason.TOO_FAR);
    }

    @Test
    void missingVehicleOrLocationIsRejectedFirst() {
        assertThat(CandidateFilter.quickCheck(driverAt(WARSZAWA_LAT, WARSZAWA_LON), null, order()))
                .contains(RejectionReason.NO_VEHICLE);
        assertThat(CandidateFilter.quickCheck(driverAt(null, null), TILT_24T, order()))
                .contains(RejectionReason.NO_LOCATION);
    }

    @Test
    void firstFailingCheckDecidesTheReason() {
        // Zły typ i za mało ładowności — zgłaszamy TYPE, bo jest sprawdzany wcześniej
        assertThat(CandidateFilter.quickCheck(driverAt(LODZ_LAT, LODZ_LON),
                vehicle(VehicleType.FLATBED, 5_000, 10), order())).contains(RejectionReason.TYPE);
    }

    // --- Filtr twardy po symulacji ---

    private static WorkTimeSimulation simulate(DriverWorkState state, double emptyDrivingMin) {
        return WorkTimeSimulator.simulate(EIGHT, state, new TripPlan(emptyDrivingMin, 60, 224, 60));
    }

    @Test
    void driverAMeetsBothDeadlines() {
        // Na załadunku 09:41 ≤ 12:00, koniec 16:10 ≤ 20:00
        WorkTimeSimulation a = simulate(new DriverWorkState(120, 1800, 120), 101);

        assertThat(CandidateFilter.deadlineCheck(a, order())).isEmpty();
    }

    @Test
    void driverBIsLateForLoadingBecauseOfDailyRest() {
        // Najbliższy kierowca: 38 min jazdy, ale po 20 min odpoczynek 11 h → 19:38 > 12:00
        WorkTimeSimulation b = simulate(new DriverWorkState(520, 2400, 60), 38);

        assertThat(CandidateFilter.deadlineCheck(b, order())).contains(RejectionReason.LATE_LOADING);
    }

    @Test
    void lateDeliveryIsRejectedButExactlyOnTimeIsAccepted() {
        WorkTimeSimulation a = simulate(new DriverWorkState(120, 1800, 120), 101); // koniec 16:10
        TransportOrder order = order();

        order.setDeliveryDeadline(EIGHT.withHour(16));
        assertThat(CandidateFilter.deadlineCheck(a, order)).contains(RejectionReason.LATE_DELIVERY);

        order.setDeliveryDeadline(EIGHT.withHour(16).withMinute(10));
        assertThat(CandidateFilter.deadlineCheck(a, order)).isEmpty();
    }

    @Test
    void exceedingWeeklyLimitIsRejectedEvenWhenOnTime() {
        // Tydzień 54 h: 101 + 224 min jazdy → 59 h 25 min > 56 h, choć terminy są dotrzymane
        WorkTimeSimulation sim = simulate(new DriverWorkState(0, 54 * 60, 0), 101);

        assertThat(CandidateFilter.deadlineCheck(sim, order())).contains(RejectionReason.WEEKLY_LIMIT);
    }
}
