package com.astra.engine.telemetry;

import java.time.Instant;

public record VehicleStatusDto(
        String vehicleId,
        String routeId,
        double latitude,
        double longitude,
        Double bearing,
        Double speedKmh,
        String status,
        Instant recordedAt
) {}
