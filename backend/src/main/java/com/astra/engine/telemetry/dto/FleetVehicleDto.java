package com.astra.engine.telemetry.dto;

import java.time.Instant;

public record FleetVehicleDto(
        String id,
        String routeId,
        double latitude,
        double longitude,
        Double bearing,
        Double speedKmh,
        int cargoPriority,
        String status,
        String intersectedAnomalyId,
        Instant updatedAt
) {}
