package com.astra.engine.routing.dto;

import java.util.List;
import java.util.Map;

public record GraphHopperAreaFeature(
        String id,
        String type,
        Map<String, Object> geometry
) {
    public static GraphHopperAreaFeature ofPolygon(String id, List<List<List<Double>>> coordinates) {
        return new GraphHopperAreaFeature(
                id,
                "Feature",
                Map.of(
                        "type", "Polygon",
                        "coordinates", coordinates
                )
        );
    }
}
