package pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Criterion;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

// K1: km dojazdu do załadunku — mniej pustych kilometrów, mniej paliwa
public class DistanceToLoadingCriterion implements Criterion {

    @Override
    public String name() {
        return "distanceToLoadingKm";
    }

    @Override
    public double rawValue(Candidate candidate, TransportOrder order) {
        return candidate.distanceToLoadingKm();
    }

    @Override
    public boolean higherIsBetter() {
        return false;
    }
}
