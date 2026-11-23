package com.astra.engine.anomaly.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AnomalyInjectRequest(
        @NotBlank(message = "anomalyType is required")
        String anomalyType,

        @NotBlank(message = "severity is required")
        String severity,

        @NotNull(message = "latitude is required")
        @DecimalMin(value = "2.90", message = "latitude must be >= 2.90")
        @DecimalMax(value = "3.30", message = "latitude must be <= 3.30")
        Double latitude,

        @NotNull(message = "longitude is required")
        @DecimalMin(value = "101.40", message = "longitude must be >= 101.40")
        @DecimalMax(value = "101.90", message = "longitude must be <= 101.90")
        Double longitude,

        @NotNull(message = "radiusMeters is required")
        @Positive(message = "radiusMeters must be positive")
        Double radiusMeters
) {}
