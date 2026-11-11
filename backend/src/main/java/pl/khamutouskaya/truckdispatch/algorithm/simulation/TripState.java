package pl.khamutouskaya.truckdispatch.algorithm.simulation;

/**
 * Stan kierowcy w chwili t od startu zlecenia.
 *
 * @param position  położenie na mapie
 * @param activity  co robi
 * @param edgeSeq   seq krawędzi, po której jedzie albo na której stoi (przerwa); null — pierwsza mila lub postój w węźle
 */
public record TripState(GeoPoint position, Activity activity, Integer edgeSeq) {
}
