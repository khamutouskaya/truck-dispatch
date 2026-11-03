package pl.khamutouskaya.truckdispatch.dto;

import pl.khamutouskaya.truckdispatch.domain.RoadNode;

public record RoadNodeResponse(
        Long id,
        String name,
        double lat,
        double lon,
        int defaultHandlingMin
) {
    public static RoadNodeResponse from(RoadNode node) {
        return new RoadNodeResponse(
                node.getId(),
                node.getName(),
                node.getLat(),
                node.getLon(),
                node.getDefaultHandlingMin()
        );
    }
}
