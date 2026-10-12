package com.astra.engine.routing;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record GraphHopperRouteRequest(
        String profile,
        List<List<Double>> points,
        @JsonProperty("points_encoded") boolean pointsEncoded,
        @JsonProperty("ch.disable") boolean chDisable,
        @JsonProperty("custom_model") GraphHopperCustomModel customModel
) {
    public static GraphHopperRouteRequest of(double originLng, double originLat, double destLng, double destLat) {
        return new GraphHopperRouteRequest(
                "car",
                List.of(List.of(originLng, originLat), List.of(destLng, destLat)),
                false,
                true,
                null
        );
    }
}
