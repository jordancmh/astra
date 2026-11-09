package com.astra.engine.mcp.dto;

import java.util.List;

public record RouteUpdateRequest(
        String vehicleId,
        String routeId,
        List<List<Double>> coordinates,
        String rationale
) {}
