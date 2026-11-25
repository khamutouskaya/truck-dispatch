package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import pl.khamutouskaya.truckdispatch.algorithm.graph.GeoDistance;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;
import pl.khamutouskaya.truckdispatch.domain.Driver;
import pl.khamutouskaya.truckdispatch.domain.RoadNode;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;
import pl.khamutouskaya.truckdispatch.domain.Vehicle;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprawdzenie kandydata. Każda metoda zwraca wszystkie niespełnione warunki swojego etapu (pusta lista = przechodzi):
 * <ol>
 *   <li>{@link #dataCheck} — brak pojazdu lub położenia; bez nich dalszych etapów nie da się policzyć;</li>
 *   <li>{@link #quickCheck} — nadwozie, ładowność, odległość w linii prostej;</li>
 *   <li>{@link #deadlineCheck} — po symulacji czasu pracy: terminy i limit tygodniowy.</li>
 * </ol>
 * Powód NO_ROUTE ustala serwis, gdy RouteFinder zwróci pustą trasę.
 */
public final class CandidateFilter {

    // Dalej niż 800 km w linii prostej: przy ~70 km/h to > 11 h jazdy, czyli co najmniej jeden odpoczynek 11 h
    public static final double MAX_STRAIGHT_DISTANCE_KM = 800;

    private CandidateFilter() {
    }

    /**
     * @param vehicle pojazd kierowcy albo null, jeśli kierowca go nie ma
     */
    public static List<RejectionReason> dataCheck(Driver driver, Vehicle vehicle) {
        List<RejectionReason> reasons = new ArrayList<>();
        if (vehicle == null) {
            reasons.add(RejectionReason.NO_VEHICLE);
        }
        if (driver.getCurrentLat() == null || driver.getCurrentLon() == null) {
            reasons.add(RejectionReason.NO_LOCATION);
        }
        return reasons;
    }

    /**
     * Wywoływać dla kierowcy, który przeszedł {@link #dataCheck}.
     */
    public static List<RejectionReason> quickCheck(Driver driver, Vehicle vehicle, TransportOrder order) {
        List<RejectionReason> reasons = new ArrayList<>();
        if (vehicle.getType() != order.getRequiredVehicleType()) {
            reasons.add(RejectionReason.TYPE);
        }
        if (vehicle.getCapacityKg() < order.getCargoWeightKg() || vehicle.getVolumeM3() < order.getCargoVolumeM3()) {
            reasons.add(RejectionReason.CAPACITY);
        }
        if (straightDistanceToLoadingKm(driver, order) > MAX_STRAIGHT_DISTANCE_KM) {
            reasons.add(RejectionReason.TOO_FAR);
        }
        return reasons;
    }

    /**
     * Filtr twardy po symulacji. Termin spełniony także wtedy, gdy przyjazd jest dokładnie o czasie.
     */
    public static List<RejectionReason> deadlineCheck(WorkTimeSimulation simulation, TransportOrder order) {
        List<RejectionReason> reasons = new ArrayList<>();
        if (simulation.arrivalAtLoading().isAfter(order.getLoadingDeadline())) {
            reasons.add(RejectionReason.LATE_LOADING);
        }
        if (simulation.finish().isAfter(order.getDeliveryDeadline())) {
            reasons.add(RejectionReason.LATE_DELIVERY);
        }
        if (simulation.weeklyLimitExceeded()) {
            reasons.add(RejectionReason.WEEKLY_LIMIT);
        }
        return reasons;
    }

    // Odległość w linii prostej od kierowcy do miejsca załadunku [km]
    public static double straightDistanceToLoadingKm(Driver driver, TransportOrder order) {
        RoadNode loading = order.getLoadingNode();
        return GeoDistance.haversineKm(driver.getCurrentLat(), driver.getCurrentLon(), loading.getLat(), loading.getLon());
    }
}
