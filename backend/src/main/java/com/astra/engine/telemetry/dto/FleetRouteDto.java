package com.astra.engine.telemetry.dto;

import com.astra.engine.common.GeoJsonLineString;

public record FleetRouteDto(
        String id,
        String vehicleId,
        String status,
        GeoJsonLineString geometry,
        Double distanceMeters,
        Long estimatedTimeMs
) {}
