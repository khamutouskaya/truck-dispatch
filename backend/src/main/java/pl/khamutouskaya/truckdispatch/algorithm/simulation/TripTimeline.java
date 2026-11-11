package pl.khamutouskaya.truckdispatch.algorithm.simulation;

import pl.khamutouskaya.truckdispatch.algorithm.graph.FirstMile;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DrivingClock;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.Stop;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.StopType;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rzeczywisty przebieg zlecenia w symulacji, liczony raz przy starcie:
 * pierwsza mila → krawędzie EMPTY → załadunek → krawędzie LOADED → rozładunek,
 * z przerwami i odpoczynkami z {@link DrivingClock} w miejscu, gdzie wypadły (także w środku krawędzi).
 * <p>
 * Czas przejazdu każdej krawędzi pochodzi z {@link TrafficModel} („prawdziwy świat”), a nie z prognozy.
 * Potem {@link #stateAt(double)} mówi, gdzie jest kierowca w dowolnej chwili — symulator tylko odpytuje oś czasu.
 */
public final class TripTimeline {

    /**
     * Fragment osi czasu.
     *
     * @param path         łamana, po której się jedzie (dla postoju — jeden punkt)
     * @param fromFraction / toFraction część łamanej przejechana na początku / końcu fragmentu (jazda w środku krawędzi)
     */
    private record Segment(Activity activity, double startMin, double endMin, List<GeoPoint> path,
                           double fromFraction, double toFraction, Integer edgeSeq) {
    }

    private final List<Segment> segments = new ArrayList<>();
    private final Map<Integer, Double> actualEdgeTimeMin = new LinkedHashMap<>();
    private final DrivingClock clock;
    private final GeoPoint unloadingPoint;
    private double loadingStartMin;

    private TripTimeline(DriverWorkState state, GeoPoint unloadingPoint) {
        this.clock = new DrivingClock(state);
        this.unloadingPoint = unloadingPoint;
    }

    public static TripTimeline build(GeoPoint driverPosition, List<PlannedEdge> emptyEdges, GeoPoint loadingPoint,
                                     double loadingMin, List<PlannedEdge> loadedEdges, GeoPoint unloadingPoint,
                                     double unloadingMin, DriverWorkState state, TrafficModel traffic) {
        TripTimeline timeline = new TripTimeline(state, unloadingPoint);

        // Pierwsza mila w linii prostej do pierwszego węzła trasy (albo od razu do załadunku)
        GeoPoint firstNode = emptyEdges.isEmpty() ? loadingPoint : emptyEdges.getFirst().geometry().getFirst();
        double firstMileMin = FirstMile.timeMin(Polyline.distanceKm(driverPosition, firstNode));
        timeline.drive(List.of(driverPosition, firstNode), firstMileMin, null);

        for (PlannedEdge edge : emptyEdges) {
            timeline.driveEdge(edge, traffic);
        }
        timeline.loadingStartMin = timeline.clock.timeMin();
        timeline.work(Activity.LOADING, loadingPoint, loadingMin);
        for (PlannedEdge edge : loadedEdges) {
            timeline.driveEdge(edge, traffic);
        }
        timeline.work(Activity.UNLOADING, unloadingPoint, unloadingMin);
        return timeline;
    }

    private void driveEdge(PlannedEdge edge, TrafficModel traffic) {
        double actualMin = traffic.actualTimeMin(edge.edgeId(), edge.baseTimeMin());
        actualEdgeTimeMin.put(edge.seq(), actualMin);
        drive(edge.geometry(), actualMin, edge.seq());
    }

    // Jazda po łamanej; postoje dzielą ją na kawałki, a sam postój stoi w punkcie, w którym wypadł
    private void drive(List<GeoPoint> path, double durationMin, Integer edgeSeq) {
        if (durationMin <= 0) {
            return; // np. kierowca stoi dokładnie w pierwszym węźle
        }
        double start = clock.timeMin();
        List<Stop> stops = clock.drive(durationMin);

        double drivenSoFar = 0;
        double pieceStart = start;
        for (Stop stop : stops) {
            double fractionAtStop = stop.drivenBeforeMin() / durationMin;
            segments.add(new Segment(Activity.DRIVING, pieceStart, stop.startMin(), path,
                    drivenSoFar / durationMin, fractionAtStop, edgeSeq));
            Activity stopActivity = stop.type() == StopType.BREAK ? Activity.BREAK : Activity.REST;
            double stopEnd = stop.startMin()
                    + (stop.type() == StopType.BREAK ? WorkTimeRules.BREAK_MIN : WorkTimeRules.DAILY_REST_MIN);
            segments.add(new Segment(stopActivity, stop.startMin(), stopEnd,
                    List.of(Polyline.pointAt(path, fractionAtStop)), 0, 0, edgeSeq));
            drivenSoFar = stop.drivenBeforeMin();
            pieceStart = stopEnd;
        }
        segments.add(new Segment(Activity.DRIVING, pieceStart, clock.timeMin(), path,
                drivenSoFar / durationMin, 1.0, edgeSeq));
    }

    private void work(Activity activity, GeoPoint point, double durationMin) {
        double start = clock.timeMin();
        clock.work(durationMin);
        segments.add(new Segment(activity, start, clock.timeMin(), List.of(point), 0, 0, null));
    }

    /**
     * Gdzie jest kierowca i co robi w chwili t [min od startu zlecenia].
     */
    public TripState stateAt(double tMin) {
        for (Segment s : segments) {
            if (tMin < s.endMin()) {
                if (tMin < s.startMin()) {
                    tMin = s.startMin(); // t < 0 — jeszcze na starcie
                }
                if (s.activity() != Activity.DRIVING) {
                    return new TripState(s.path().getFirst(), s.activity(), s.edgeSeq());
                }
                double progress = (tMin - s.startMin()) / (s.endMin() - s.startMin());
                double fraction = s.fromFraction() + progress * (s.toFraction() - s.fromFraction());
                return new TripState(Polyline.pointAt(s.path(), fraction), Activity.DRIVING, s.edgeSeq());
            }
        }
        return new TripState(unloadingPoint, Activity.FINISHED, null);
    }

    // Początek załadunku [min] — od tej chwili zlecenie jest IN_PROGRESS
    public double loadingStartMin() {
        return loadingStartMin;
    }

    // Koniec rozładunku [min] — zlecenie DONE
    public double finishMin() {
        return clock.timeMin();
    }

    // Rzeczywisty czas jazdy każdej krawędzi (seq → min), bez postojów — „prawda” do sprawdzenia samouczenia w testach
    public Map<Integer, Double> actualEdgeTimeMin() {
        return Collections.unmodifiableMap(actualEdgeTimeMin);
    }

    // Liczniki czasu pracy kierowcy po zakończeniu zlecenia
    public DriverWorkState endState() {
        return clock.state();
    }

    public int breakCount() {
        return clock.breakCount();
    }

    public int restCount() {
        return clock.restCount();
    }
}
