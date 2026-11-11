package pl.khamutouskaya.truckdispatch.algorithm.assignment.criterion;

import pl.khamutouskaya.truckdispatch.algorithm.assignment.Candidate;
import pl.khamutouskaya.truckdispatch.algorithm.assignment.Criterion;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

// K2: godziny od „teraz” do przyjazdu na załadunek, z przerwami i odpoczynkami
public class TimeToLoadingCriterion implements Criterion {

    @Override
    public String name() {
        return "timeToLoadingH";
    }

    @Override
    public double rawValue(Candidate candidate, TransportOrder order) {
        return candidate.simulation().arrivalAtLoadingMin() / 60.0;
    }

    @Override
    public boolean higherIsBetter() {
        return false;
    }
}
