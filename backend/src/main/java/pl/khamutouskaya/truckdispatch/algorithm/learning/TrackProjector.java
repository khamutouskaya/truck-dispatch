package pl.khamutouskaya.truckdispatch.algorithm.learning;

import pl.khamutouskaya.truckdispatch.algorithm.simulation.GeoPoint;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.PlannedEdge;
import pl.khamutouskaya.truckdispatch.algorithm.simulation.Polyline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rzeczywisty czas jazdy po każdej krawędzi trasy, odtworzony ze śladu GPS (docs/karta-i-algorytm.md, krok 2 i 11).
 * <ol>
 *   <li>Każdy punkt rzutujemy na geometrię trasy: t = (AP·AB) / |AB|², obcięte do [0, 1], w lokalnym układzie
 *       x = Δlon·cos(lat)·111,32 km, y = Δlat·110,57 km. Wynik: przebyta droga s [km od początku trasy].</li>
 *   <li>Punkty dalej niż 5 km od trasy pomijamy (np. pierwsza mila, objazd).</li>
 *   <li>Szukamy tylko do przodu: bieżąca krawędź i kilka następnych. Wybieramy najbliższe położenie
 *       <b>przed nami na trasie</b>, a nie najbliższe w przestrzeni: spośród dopasowań, które nie cofają s
 *       i są prawie tak blisko jak najlepsze (± 0,5 km), bierzemy to o najmniejszym s. Dzięki temu trasa
 *       wracająca tą samą drogą (A→B, potem B→A) — punkt leży wtedy dokładnie na obu krawędziach —
 *       nie „przeskakuje” na krawędź powrotną ani nie zostaje na krawędzi wyjazdowej.</li>
 *   <li>Para kolejnych punktów, między którymi przebyto &lt; 50 m, to postój (przerwa, załadunek) — nie liczymy
 *       go jako jazdy. Czas pary w ruchu dzielimy między krawędzie proporcjonalnie do przejechanych km.</li>
 *   <li>Wynik tylko dla krawędzi pokrytych śladem w całości.</li>
 * </ol>
 * Dokładność: postój zaczynający się w środku pary punktów jest liczony jako jazda do końca tej pary —
 * błąd co najwyżej jeden odstęp próbkowania na postój (przy próbce co 1 min: ułamek procenta czasu krawędzi).
 */
public final class TrackProjector {

    public static final double MAX_OFF_ROUTE_KM = 5.0;
    public static final double MIN_MOVE_KM = 0.05;
    // Ile krawędzi do przodu szukamy dopasowania
    static final int LOOKAHEAD_EDGES = 3;
    // Tolerancja pokrycia krawędzi śladem [km]
    static final double COVERAGE_TOLERANCE_KM = 0.5;
    // O ile s może „cofnąć się” przy dopasowaniu (szum GPS), zanim uznamy dopasowanie za ruch wstecz [km];
    // ta sama wartość: kandydaci tak bliscy jak najlepszy ± tyle km uznani za równie dobrych
    static final double BACKTRACK_TOLERANCE_KM = 0.5;

    private static final double KM_PER_DEG_LAT = 110.57;
    private static final double KM_PER_DEG_LON_AT_EQUATOR = 111.32;

    private TrackProjector() {
    }

    /**
     * @param route krawędzie trasy w kolejności jazdy (z geometrią)
     * @param track punkty GPS w kolejności czasu
     * @return seq krawędzi → rzeczywisty czas jazdy po niej [min], tylko krawędzie pokryte w całości
     */
    public static Map<Integer, Double> drivingTimeByEdge(List<PlannedEdge> route, List<TrackPoint> track) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        if (route.isEmpty() || track.size() < 2) {
            return result;
        }

        // Granice krawędzi na osi s: krawędź i to [start[i], start[i+1]]
        double[] start = new double[route.size() + 1];
        for (int i = 0; i < route.size(); i++) {
            start[i + 1] = start[i] + Polyline.lengthKm(route.get(i).geometry());
        }

        // Rzutowanie punktów na trasę → przebyta droga s (null = punkt poza trasą)
        double[] s = new double[track.size()];
        boolean[] onRoute = new boolean[track.size()];
        int currentEdge = 0;
        double lastS = 0;
        for (int p = 0; p < track.size(); p++) {
            List<Match> forward = new ArrayList<>(); // dopasowania, które nie cofają s
            Match best = null;                        // najbliższe w ogóle (gdy nic nie ma przed nami)
            int lastEdge = Math.min(route.size() - 1, currentEdge + LOOKAHEAD_EDGES);
            for (int e = currentEdge; e <= lastEdge; e++) {
                Match m = project(track.get(p).position(), route.get(e).geometry(), e, start[e]);
                if (best == null || m.distanceKm() < best.distanceKm()) {
                    best = m;
                }
                if (m.s() >= lastS - BACKTRACK_TOLERANCE_KM) {
                    forward.add(m);
                }
            }
            if (!forward.isEmpty()) {
                double nearest = forward.stream().mapToDouble(Match::distanceKm).min().orElseThrow();
                best = forward.stream()
                        .filter(m -> m.distanceKm() <= nearest + BACKTRACK_TOLERANCE_KM)
                        .min(Comparator.comparingDouble(Match::s))
                        .orElseThrow();
            }
            if (best.distanceKm() <= MAX_OFF_ROUTE_KM) {
                lastS = Math.max(lastS, best.s()); // s nie maleje
                currentEdge = Math.max(currentEdge, best.edgeIndex());
                s[p] = lastS;
                onRoute[p] = true;
            }
        }

        // Czas jazdy pary punktów rozkładamy na krawędzie, przez które przejechano
        double[] drivingMin = new double[route.size()];
        double minS = Double.MAX_VALUE, maxS = -Double.MAX_VALUE;
        for (int p = 0; p + 1 < track.size(); p++) {
            if (!onRoute[p] || !onRoute[p + 1]) {
                continue;
            }
            minS = Math.min(minS, s[p]);
            maxS = Math.max(maxS, s[p + 1]);
            double ds = s[p + 1] - s[p];
            if (ds < MIN_MOVE_KM) {
                continue; // postój
            }
            double dt = track.get(p + 1).tMin() - track.get(p).tMin();
            for (int e = 0; e < route.size(); e++) {
                double overlap = Math.min(s[p + 1], start[e + 1]) - Math.max(s[p], start[e]);
                if (overlap > 0) {
                    drivingMin[e] += dt * overlap / ds;
                }
            }
        }

        for (int e = 0; e < route.size(); e++) {
            boolean covered = minS <= start[e] + COVERAGE_TOLERANCE_KM && maxS >= start[e + 1] - COVERAGE_TOLERANCE_KM;
            if (covered && drivingMin[e] > 0) {
                result.put(route.get(e).seq(), drivingMin[e]);
            }
        }
        return result;
    }

    // Najbliższy punkt łamanej krawędzi: odległość od trasy i przebyta droga s
    private record Match(int edgeIndex, double s, double distanceKm) {
    }

    private static Match project(GeoPoint p, List<GeoPoint> line, int edgeIndex, double edgeStartS) {
        double cosLat = Math.cos(Math.toRadians(p.lat()));
        double bestDistance = Double.MAX_VALUE;
        double bestS = edgeStartS;
        double walked = edgeStartS;
        for (int i = 1; i < line.size(); i++) {
            GeoPoint a = line.get(i - 1);
            GeoPoint b = line.get(i);
            // Lokalny układ w km z początkiem w P
            double ax = (a.lon() - p.lon()) * cosLat * KM_PER_DEG_LON_AT_EQUATOR, ay = (a.lat() - p.lat()) * KM_PER_DEG_LAT;
            double bx = (b.lon() - p.lon()) * cosLat * KM_PER_DEG_LON_AT_EQUATOR, by = (b.lat() - p.lat()) * KM_PER_DEG_LAT;
            double abx = bx - ax, aby = by - ay;
            double ab2 = abx * abx + aby * aby;
            // t = (AP·AB) / |AB|², AP = P − A = −A (P w początku układu)
            double t = ab2 == 0 ? 0 : Math.clamp((-ax * abx - ay * aby) / ab2, 0.0, 1.0);
            double qx = ax + t * abx, qy = ay + t * aby;
            double distance = Math.hypot(qx, qy);
            double segmentKm = Polyline.lengthKm(List.of(a, b));
            if (distance < bestDistance) {
                bestDistance = distance;
                bestS = walked + t * segmentKm;
            }
            walked += segmentKm;
        }
        return new Match(edgeIndex, bestS, bestDistance);
    }
}
