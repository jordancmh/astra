package com.astra.engine.telemetry.dto;

import java.time.Instant;
import java.util.List;

public record FleetStateResponse(
        FleetSummaryDto summary,
        List<FleetVehicleDto> vehicles,
        List<FleetRouteDto> routes,
        Instant timestamp
) {}
