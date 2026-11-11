package pl.khamutouskaya.truckdispatch.algorithm.graph;

/**
 * To, co wyszukiwanie trasy musi wiedzieć o pojeździe na danym odcinku podróży.
 * Ten sam pojazd ma dwa profile: pusty (dojazd do załadunku) i załadowany (załadunek → rozładunek).
 *
 * @param heightM      wysokość pojazdu [m] — porównywana z maxHeightM krawędzi
 * @param totalWeightT masa całkowita [t] — porównywana z maxWeightT krawędzi
 * @param loaded       czy pojazd wiezie ładunek (wpływa na prędkość w terenie pagórkowatym i górskim)
 */
public record VehicleProfile(double heightM, double totalWeightT, boolean loaded) {

    public VehicleProfile {
        if (heightM <= 0 || totalWeightT <= 0) {
            throw new IllegalArgumentException("Vehicle height and weight must be positive");
        }
    }

    // Pusty pojazd: masa = masa własna
    public static VehicleProfile empty(double heightM, int emptyWeightKg) {
        return new VehicleProfile(heightM, emptyWeightKg / 1000.0, false);
    }

    // Załadowany pojazd: masa = masa własna + ładunek
    public static VehicleProfile loaded(double heightM, int emptyWeightKg, int cargoWeightKg) {
        return new VehicleProfile(heightM, (emptyWeightKg + cargoWeightKg) / 1000.0, true);
    }
}
