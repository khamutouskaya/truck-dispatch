package pl.khamutouskaya.truckdispatch.algorithm.worktime;

/**
 * Liczniki czasu jazdy kierowcy w danej chwili [min].
 *
 * @param drivingTodayMin      jazda od ostatniego odpoczynku dobowego
 * @param drivingWeekMin       jazda w bieżącym tygodniu
 * @param continuousDrivingMin jazda od ostatniej przerwy (lub odpoczynku)
 */
public record DriverWorkState(double drivingTodayMin, double drivingWeekMin, double continuousDrivingMin) {

    public DriverWorkState {
        if (drivingTodayMin < 0 || drivingWeekMin < 0 || continuousDrivingMin < 0) {
            throw new IllegalArgumentException("Driving times must be non-negative");
        }
    }

    // Wypoczęty kierowca na początku tygodnia
    public static DriverWorkState rested() {
        return new DriverWorkState(0, 0, 0);
    }

    // Ile jeszcze wolno jechać dziś [min] (K3); nie mniej niż 0
    public double remainingDrivingTodayMin() {
        return Math.max(0, WorkTimeRules.MAX_DAILY_DRIVING_MIN - drivingTodayMin);
    }

    // Ile jeszcze wolno jechać w tym tygodniu [min]; nie mniej niż 0
    public double remainingDrivingWeekMin() {
        return Math.max(0, WorkTimeRules.MAX_WEEKLY_DRIVING_MIN - drivingWeekMin);
    }
}
