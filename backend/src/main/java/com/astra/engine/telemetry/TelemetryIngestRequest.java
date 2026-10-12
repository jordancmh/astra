package com.astra.engine.telemetry;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TelemetryIngestRequest(
        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "2.90", message = "latitude must be within Klang Valley (>= 2.90)")
        @DecimalMax(value = "3.30", message = "latitude must be within Klang Valley (<= 3.30)")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "101.40", message = "longitude must be within Klang Valley (>= 101.40)")
        @DecimalMax(value = "101.90", message = "longitude must be within Klang Valley (<= 101.90)")
        Double longitude,

        Double bearing,
        Double speedKmh,
        String routeId
) {
}
