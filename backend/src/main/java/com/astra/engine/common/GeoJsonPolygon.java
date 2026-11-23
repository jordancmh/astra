package com.astra.engine.common;

import java.util.List;

public record GeoJsonPolygon(
        String type,
        List<List<List<Double>>> coordinates
) {
    public GeoJsonPolygon(List<List<List<Double>>> coordinates) {
        this("Polygon", coordinates);
    }

    public static GeoJsonPolygon ofSingleRing(List<List<Double>> ringCoordinates) {
        return new GeoJsonPolygon("Polygon", List.of(ringCoordinates));
    }
}
