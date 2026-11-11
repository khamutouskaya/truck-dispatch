package pl.khamutouskaya.truckdispatch.algorithm.worktime;

/**
 * Plan zlecenia w czasie [min]: jazda pusta → załadunek → jazda z ładunkiem → rozładunek.
 * Czasy jazdy pochodzą z tras (Route.totalTimeMin), czasy załadunku/rozładunku — ze zlecenia.
 */
public record TripPlan(double emptyDrivingMin, double loadingMin, double loadedDrivingMin, double unloadingMin) {

    public TripPlan {
        if (emptyDrivingMin < 0 || loadingMin < 0 || loadedDrivingMin < 0 || unloadingMin < 0) {
            throw new IllegalArgumentException("Trip durations must be non-negative");
        }
    }
}
