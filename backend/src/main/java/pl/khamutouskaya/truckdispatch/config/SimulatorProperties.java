package pl.khamutouskaya.truckdispatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Ustawienia symulatora ruchu kierowców (application.yaml, simulator.*).
 *
 * @param enabled    czy uruchamiać symulator co tickMs (w testach wyłączony)
 * @param tickMs     odstęp między tickami w czasie rzeczywistym [ms]
 * @param speedup    przyspieszenie czasu: 60 → 1 s rzeczywista = 1 min symulacji
 * @param seed       ziarno generatora szumu — powtarzalny eksperyment
 * @param noise      szum czasu przejazdu: ±noise (0,10 = ±10%)
 * @param congestion „prawdziwe” korki ukryte przed algorytmem — samouczenie ma je odkryć
 */
@ConfigurationProperties(prefix = "simulator")
public record SimulatorProperties(
        boolean enabled,
        long tickMs,
        double speedup,
        long seed,
        double noise,
        List<Congestion> congestion
) {
    public SimulatorProperties {
        if (tickMs <= 0 || speedup <= 0) {
            throw new IllegalArgumentException("simulator.tick-ms and simulator.speedup must be positive");
        }
        congestion = congestion == null ? List.of() : List.copyOf(congestion);
    }

    /**
     * Korek na jednej krawędzi (kierunek ma znaczenie: Warszawa → Stryków to inna krawędź niż Stryków → Warszawa).
     */
    public record Congestion(String from, String to, double factor) {
    }

    // O ile minut symulacji przesuwa się czas w jednym ticku
    public double stepMin() {
        return tickMs / 60_000.0 * speedup;
    }
}
