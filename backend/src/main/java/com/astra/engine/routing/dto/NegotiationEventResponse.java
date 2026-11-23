package com.astra.engine.routing.dto;

import java.time.Instant;
import java.util.UUID;

public record NegotiationEventResponse(
        UUID id,
        String anomalyId,
        String vehicleId,
        String affectedRoutes,
        String eventMessage,
        Instant createdAt
) {}
