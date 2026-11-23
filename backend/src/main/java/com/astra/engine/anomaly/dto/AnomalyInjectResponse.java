package com.astra.engine.anomaly.dto;

import java.time.Instant;
import java.util.List;

public record AnomalyInjectResponse(
        String anomalyId,
        String anomalyType,
        String severity,
        List<String> affectedRouteIds,
        List<String> affectedVehicleIds,
        Instant injectedAt
) {}
