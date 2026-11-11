package pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Criterion;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

// K5: wykorzystanie ładowności = masa ładunku / ładowność — duże auta zostają dla dużych ładunków
public class VehicleUtilizationCriterion implements Criterion {

    @Override
    public String name() {
        return "vehicleUtilization";
    }

    @Override
    public double rawValue(Candidate candidate, TransportOrder order) {
        return (double) order.getCargoWeightKg() / candidate.vehicleCapacityKg();
    }

    @Override
    public boolean higherIsBetter() {
        return true;
    }
}
