package pl.khamutouskaya.truckdispatch.algorithm.graph;

import pl.khamutouskaya.truckdispatch.domain.RoadType;
import pl.khamutouskaya.truckdispatch.domain.SurfaceQuality;
import pl.khamutouskaya.truckdispatch.domain.Terrain;

// Krawędź skierowana w pamięci: odcinek drogi z węzła fromId do węzła toId.
// Zawiera tylko właściwości drogi; czas przejazdu liczy się wzorem dla konkretnego pojazdu.
public record GraphEdge(
        long id,
        long fromId,
        long toId,
        double distanceKm,
        RoadType roadType,
        SurfaceQuality surfaceQuality,
        Terrain terrain,
        Double maxWeightT,   // null = brak ograniczenia
        Double maxHeightM,   // null = brak ograniczenia
        double delayFactor
) {
}
