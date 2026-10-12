package com.astra.engine.telemetry;

import java.time.Instant;

public record TelemetryIngestResponse(
        String vehicleId,
        String status,
        String intersectedAnomalyId,
        Instant updatedAt
) {
}
