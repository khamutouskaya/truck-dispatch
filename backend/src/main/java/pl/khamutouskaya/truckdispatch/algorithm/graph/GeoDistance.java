package pl.khamutouskaya.truckdispatch.algorithm.graph;

/**
 * Odległość po powierzchni Ziemi (wzór haversine). Ziemia przybliżona kulą o promieniu 6371 km.
 */
public final class GeoDistance {

    public static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() {
    }

    // Odległość w linii prostej (po łuku koła wielkiego) między dwoma punktami [km]
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double haversineKm(GraphNode a, GraphNode b) {
        return haversineKm(a.lat(), a.lon(), b.lat(), b.lon());
    }
}
