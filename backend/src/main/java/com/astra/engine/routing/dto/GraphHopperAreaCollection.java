package com.astra.engine.routing.dto;

import java.util.List;

public record GraphHopperAreaCollection(
        String type,
        List<GraphHopperAreaFeature> features
) {
    public static GraphHopperAreaCollection of(List<GraphHopperAreaFeature> features) {
        return new GraphHopperAreaCollection("FeatureCollection", features != null ? features : List.of());
    }
}
