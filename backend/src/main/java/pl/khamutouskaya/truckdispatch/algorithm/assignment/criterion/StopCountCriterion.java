package pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Criterion;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

// K4: liczba przerw 45 min i odpoczynków 11 h w trakcie zlecenia — każdy postój to ryzyko opóźnienia
public class StopCountCriterion implements Criterion {

    @Override
    public String name() {
        return "stopCount";
    }

    @Override
    public double rawValue(Candidate candidate, TransportOrder order) {
        return candidate.simulation().stopCount();
    }

    @Override
    public boolean higherIsBetter() {
        return false;
    }
}
