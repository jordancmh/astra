package com.astra.engine.anomaly.dto;

import java.time.Instant;

public record AnomalyClearResponse(
        int clearedCount,
        Instant timestamp
) {}
