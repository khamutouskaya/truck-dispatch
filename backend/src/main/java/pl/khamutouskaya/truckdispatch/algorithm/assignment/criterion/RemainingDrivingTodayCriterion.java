package pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Criterion;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

// K3: godziny jazdy, które zostaną kierowcy na dziś po zleceniu — elastyczność na kolejne zadania
public class RemainingDrivingTodayCriterion implements Criterion {

    @Override
    public String name() {
        return "remainingDrivingTodayH";
    }

    @Override
    public double rawValue(Candidate candidate, TransportOrder order) {
        return candidate.simulation().endState().remainingDrivingTodayMin() / 60.0;
    }

    @Override
    public boolean higherIsBetter() {
        return true;
    }
}
