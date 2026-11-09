package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;

import java.time.Instant;

public record RouteUpdateResult(
        String status,
        String vehicleId,
        String routeId,
        int coordinatesCount,
        GeoJsonLineString geometry,
        Double distanceMeters,
        Long estimatedTimeMs,
        Instant updatedAt
) {
    public RouteUpdateResult(
            String status,
            String vehicleId,
            String routeId,
            int coordinatesCount,
            GeoJsonLineString geometry,
            Instant updatedAt
    ) {
        this(status, vehicleId, routeId, coordinatesCount, geometry, null, null, updatedAt);
    }
}
