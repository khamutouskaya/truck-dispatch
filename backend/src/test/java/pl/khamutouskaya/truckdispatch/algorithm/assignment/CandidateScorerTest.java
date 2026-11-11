package pl.khamutouskaya.truckdispatch.algorithm.assignment;

import org.junit.jupiter.api.Test;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.DriverWorkState;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.TripPlan;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulation;
import pl.khamutouskaya.truckdispatch.algorithm.worktime.WorkTimeSimulator;
import pl.khamutouskaya.truckdispatch.domain.TransportOrder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Przykład z docs/karta-i-algorytm.md, część 4: zlecenie Łódź → Kraków, 18 t, start 08:00.
 * Symulacje kierowców A i E liczone prawdziwym WorkTimeSimulator — cały łańcuch od czasu jazdy do score.
 */
class CandidateScorerTest {

    private static final double EPS = 1e-9;
    private static final LocalDateTime EIGHT = LocalDateTime.of(2026, 11, 11, 8, 0);

    private static final TransportOrder ORDER = order(18_000);

    private static TransportOrder order(int cargoWeightKg) {
        TransportOrder order = new TransportOrder();
        order.setCargoWeightKg(cargoWeightKg);
        return order;
    }

    // A: Warszawa → Łódź 135 km, 1 h 41 min; dziś 2 h, bez przerwy 2 h, tydzień 30 h; tent 24 t
    private static Candidate driverA() {
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT,
                new DriverWorkState(120, 1800, 120), new TripPlan(101, 60, 224, 60));
        return new Candidate(1, "A", 24_000, 135, sim);
    }

    // E: Katowice → Łódź 200 km, 2 h 30 min; wypoczęty, tydzień 10 h; tent 24 t
    private static Candidate driverE() {
        WorkTimeSimulation sim = WorkTimeSimulator.simulate(EIGHT,
                new DriverWorkState(0, 600, 0), new TripPlan(150, 60, 224, 60));
        return new Candidate(5, "E", 24_000, 200, sim);
    }

    // D: w dokumencie zajęty, zwalnia się o 09:30 w Radomiu. Kandydaci zajęci to wersja rozwojowa,
    // więc tu tylko wartości do oceny, liczone od 08:00: przyjazd 11:39 = 219 min, zapas na dziś 7 min, 1 przerwa.
    private static Candidate driverD() {
        WorkTimeSimulation sim = new WorkTimeSimulation(EIGHT, 219, 608, 1, 0,
                new DriverWorkState(533, 1733, 143), false);
        return new Candidate(4, "D", 20_000, 140, sim);
    }

    private static CandidateScore scoreOf(List<CandidateScore> ranking, String name) {
        return ranking.stream().filter(s -> s.candidate().driverName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void baseVersionExampleGivesA085AndE045() {
        // Dokument, „Ten sam przykład w wersji bazowej”: A = 0,85, E = 0,45
        List<CandidateScore> ranking = CandidateScorer.rank(List.of(driverE(), driverA()), ORDER,
                DefaultCriteria.withDefaultWeights());

        assertThat(ranking).extracting(s -> s.candidate().driverName()).containsExactly("A", "E");
        CandidateScore a = ranking.get(0);
        CandidateScore e = ranking.get(1);
        assertThat(a.score()).isCloseTo(0.85, within(EPS));
        assertThat(e.score()).isCloseTo(0.45, within(EPS));

        // Surowe wartości jak w tabeli „Krok 7”
        assertThat(a.rawValues().get("distanceToLoadingKm")).isEqualTo(135.0);
        assertThat(a.rawValues().get("timeToLoadingH")).isCloseTo(101 / 60.0, within(EPS));      // 1,68 h
        assertThat(a.rawValues().get("remainingDrivingTodayH")).isCloseTo(95 / 60.0, within(EPS)); // 1,58 h
        assertThat(a.rawValues().get("stopCount")).isEqualTo(1.0);
        assertThat(a.rawValues().get("vehicleUtilization")).isCloseTo(0.75, within(EPS));
        assertThat(e.rawValues().get("remainingDrivingTodayH")).isCloseTo(166 / 60.0, within(EPS)); // 2,77 h

        // A najlepszy w K1, K2; E w K3; K4 i K5 równe → wszyscy 1
        assertThat(a.normalizedValues()).containsEntry("distanceToLoadingKm", 1.0)
                .containsEntry("timeToLoadingH", 1.0)
                .containsEntry("remainingDrivingTodayH", 0.0)
                .containsEntry("stopCount", 1.0)
                .containsEntry("vehicleUtilization", 1.0);
    }

    @Test
    void fullExampleWithThreeCandidatesMatchesDocumentWithoutRiskPenalty() {
        // Dokument: A 0,783; D 0,531 (przed karą ryzyka); E 0,475
        List<CandidateScore> ranking = CandidateScorer.rank(List.of(driverA(), driverD(), driverE()), ORDER,
                DefaultCriteria.withDefaultWeights());

        assertThat(scoreOf(ranking, "A").score()).isCloseTo(0.783, within(0.0005));
        assertThat(scoreOf(ranking, "D").score()).isCloseTo(0.531, within(0.0005));
        assertThat(scoreOf(ranking, "E").score()).isCloseTo(0.475, within(0.0005));
        // Bez kary ryzyka D wyprzedza E; dopiero kara (wersja rozwojowa) zmienia kolejność
        assertThat(ranking).extracting(s -> s.candidate().driverName()).containsExactly("A", "D", "E");

        // K5: D ma auto 20 t → 0,9 wykorzystania → jedyny z normą 1
        assertThat(scoreOf(ranking, "D").normalizedValues().get("vehicleUtilization")).isEqualTo(1.0);
        assertThat(scoreOf(ranking, "A").normalizedValues().get("vehicleUtilization")).isEqualTo(0.0);
    }

    @Test
    void differentWeightsChangeTheWinner() {
        // Analiza wrażliwości: liczy się tylko zapas jazdy na dziś (K3) → wygrywa E
        List<CandidateScore> ranking = CandidateScorer.rank(List.of(driverA(), driverE()), ORDER,
                DefaultCriteria.withWeights(0, 0, 1, 0, 0));

        assertThat(ranking.getFirst().candidate().driverName()).isEqualTo("E");
        assertThat(ranking.getFirst().score()).isCloseTo(1.0, within(EPS));
    }

    @Test
    void singleCandidateGetsScoreOne() {
        List<CandidateScore> ranking = CandidateScorer.rank(List.of(driverA()), ORDER,
                DefaultCriteria.withDefaultWeights());

        assertThat(ranking).hasSize(1);
        assertThat(ranking.getFirst().score()).isCloseTo(1.0, within(EPS));
    }

    @Test
    void noCandidatesGiveEmptyRanking() {
        assertThat(CandidateScorer.rank(List.of(), ORDER, DefaultCriteria.withDefaultWeights())).isEmpty();
    }

    @Test
    void tieIsBrokenByEarlierArrivalThenByShorterDistance() {
        // Wagi tylko na K5, a wszyscy mają to samo auto → score 1 u wszystkich, decyduje remis
        WorkTimeSimulation early = WorkTimeSimulator.simulate(EIGHT, DriverWorkState.rested(), new TripPlan(60, 0, 0, 0));
        WorkTimeSimulation late = WorkTimeSimulator.simulate(EIGHT, DriverWorkState.rested(), new TripPlan(90, 0, 0, 0));
        Candidate lateNear = new Candidate(1, "lateNear", 24_000, 50, late);
        Candidate earlyFar = new Candidate(2, "earlyFar", 24_000, 120, early);
        Candidate earlyNear = new Candidate(3, "earlyNear", 24_000, 80, early);

        List<CandidateScore> ranking = CandidateScorer.rank(List.of(lateNear, earlyFar, earlyNear), ORDER,
                DefaultCriteria.withWeights(0, 0, 0, 0, 1));

        assertThat(ranking).extracting(s -> s.candidate().driverName())
                .containsExactly("earlyNear", "earlyFar", "lateNear");
    }

    @Test
    void rejectsWeightsThatDoNotSumToOne() {
        assertThatThrownBy(() -> CandidateScorer.rank(List.of(driverA()), ORDER,
                DefaultCriteria.withWeights(0.3, 0.3, 0.3, 0.3, 0.3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1");
    }

    @Test
    void rejectsDuplicateCriterionNames() {
        List<WeightedCriterion> duplicated = List.of(
                DefaultCriteria.withDefaultWeights().get(0),
                new WeightedCriterion(DefaultCriteria.withDefaultWeights().get(0).criterion(), 0.75));

        assertThatThrownBy(() -> CandidateScorer.rank(List.of(driverA()), ORDER, duplicated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique");
    }

    @Test
    void defaultWeightsSumToOne() {
        double sum = DefaultCriteria.withDefaultWeights().stream().mapToDouble(WeightedCriterion::weight).sum();
        assertThat(sum).isCloseTo(1.0, within(EPS));
    }
}
