package com.astra.engine.routing;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RouteCalculateRequest(
        @NotBlank(message = "routeId is required")
        String routeId,

        @NotBlank(message = "vehicleId is required")
        String vehicleId,

        @NotNull(message = "originLat is required")
        @DecimalMin(value = "2.90", message = "originLat must be >= 2.90")
        @DecimalMax(value = "3.30", message = "originLat must be <= 3.30")
        Double originLat,

        @NotNull(message = "originLng is required")
        @DecimalMin(value = "101.40", message = "originLng must be >= 101.40")
        @DecimalMax(value = "101.90", message = "originLng must be <= 101.90")
        Double originLng,

        @NotNull(message = "destLat is required")
        @DecimalMin(value = "2.90", message = "destLat must be >= 2.90")
        @DecimalMax(value = "3.30", message = "destLat must be <= 3.30")
        Double destLat,

        @NotNull(message = "destLng is required")
        @DecimalMin(value = "101.40", message = "destLng must be >= 101.40")
        @DecimalMax(value = "101.90", message = "destLng must be <= 101.90")
        Double destLng,

        List<String> avoidAnomalyIds
) {
    public RouteCalculateRequest {
        if (avoidAnomalyIds == null) {
            avoidAnomalyIds = List.of();
        }
    }
}
