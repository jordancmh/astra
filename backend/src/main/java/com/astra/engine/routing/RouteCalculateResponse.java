package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;

public record RouteCalculateResponse(
        String routeId,
        String status,
        Double distanceMeters,
        Long estimatedTimeMs,
        GeoJsonLineString geometry
) {}
