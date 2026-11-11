package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.algorithm.graph.GeoDistance;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;

import java.util.Optional;

/**
 * Odsiew kandydatów w dwóch etapach (kroki 3 i 6 algorytmu, docs/karta-i-algorytm.md):
 * <ol>
 *   <li>{@link #quickCheck} — przed szukaniem tras, prawie bez kosztu; odpada większość nieodpowiednich;</li>
 *   <li>{@link #deadlineCheck} — po symulacji czasu pracy: terminy i limit tygodniowy.</li>
 * </ol>
 * Powód NO_ROUTE ustala serwis, gdy RouteFinder zwróci pustą trasę.
 * Każda metoda zwraca pierwszy napotkany powód albo Optional.empty(), jeśli kierowca przechodzi dalej.
 */
public final class CandidateFilter {

    // Dalej niż 800 km w linii prostej: przy ~70 km/h to > 11 h jazdy, czyli co najmniej jeden odpoczynek 11 h
    public static final double MAX_STRAIGHT_DISTANCE_KM = 800;

    private CandidateFilter() {
    }

    /**
     * Szybki odsiew: dane kierowcy, nadwozie, ładowność, odległość w linii prostej.
     *
     * @param vehicle pojazd kierowcy albo null, jeśli kierowca go nie ma
     */
    public static Optional<RejectionReason> quickCheck(Driver driver, Vehicle vehicle, TransportOrder order) {
        if (vehicle == null) {
            return Optional.of(RejectionReason.NO_VEHICLE);
        }
        if (driver.getCurrentLat() == null || driver.getCurrentLon() == null) {
            return Optional.of(RejectionReason.NO_LOCATION);
        }
        if (vehicle.getType() != order.getRequiredVehicleType()) {
            return Optional.of(RejectionReason.TYPE);
        }
        if (vehicle.getCapacityKg() < order.getCargoWeightKg() || vehicle.getVolumeM3() < order.getCargoVolumeM3()) {
            return Optional.of(RejectionReason.CAPACITY);
        }
        if (straightDistanceToLoadingKm(driver, order) > MAX_STRAIGHT_DISTANCE_KM) {
            return Optional.of(RejectionReason.TOO_FAR);
        }
        return Optional.empty();
    }

    /**
     * Filtr twardy po symulacji. Termin spełniony także wtedy, gdy przyjazd jest dokładnie o czasie.
     */
    public static Optional<RejectionReason> deadlineCheck(WorkTimeSimulation simulation, TransportOrder order) {
        if (simulation.arrivalAtLoading().isAfter(order.getLoadingDeadline())) {
            return Optional.of(RejectionReason.LATE_LOADING);
        }
        if (simulation.finish().isAfter(order.getDeliveryDeadline())) {
            return Optional.of(RejectionReason.LATE_DELIVERY);
        }
        if (simulation.weeklyLimitExceeded()) {
            return Optional.of(RejectionReason.WEEKLY_LIMIT);
        }
        return Optional.empty();
    }

    // Odległość w linii prostej od kierowcy do miejsca załadunku [km]
    public static double straightDistanceToLoadingKm(Driver driver, TransportOrder order) {
        RoadNode loading = order.getLoadingNode();
        return GeoDistance.haversineKm(driver.getCurrentLat(), driver.getCurrentLon(), loading.getLat(), loading.getLon());
    }
}
