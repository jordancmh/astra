package com.astra.engine.anomaly.dto;

import com.astra.engine.common.GeoJsonPolygon;

import java.time.Instant;
import java.util.List;

public record AnomalyDto(
        String id,
        String anomalyId,
        String anomalyType,
        String severity,
        Double radiusMeters,
        List<Double> center,
        GeoJsonPolygon polygon,
        Instant injectedAt,
        boolean isActive
) {}
