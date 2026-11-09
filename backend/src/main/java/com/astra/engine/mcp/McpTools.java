package com.astra.engine.mcp;

import com.astra.engine.routing.RouteUpdateResult;
import com.astra.engine.routing.RoutingService;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.VehicleStatusDto;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Service
public class McpTools {

    private final TelemetryService telemetryService;
    private final RoutingService routingService;
    private final ObjectMapper objectMapper;

    public McpTools(TelemetryService telemetryService,
                    RoutingService routingService,
                    ObjectMapper objectMapper) {
        this.telemetryService = telemetryService;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
    }

    @McpTool(name = "getFleetState", description = "Reads current vehicle telemetry and operational status for active delivery vehicles in the Klang Valley.")
    public String getFleetState(
            @McpToolParam(description = "Optional vehicle ID (e.g. V-001) to filter for a single vehicle. If omitted, returns all fleet vehicles.", required = false)
            String vehicleId) {
        try {
            List<VehicleStatusDto> fleet = telemetryService.getFleetState(vehicleId);
            return objectMapper.writeValueAsString(Map.of(
                    "status", "SUCCESS",
                    "count", fleet.size(),
                    "vehicles", fleet
            ));
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch fleet state: " + e.getMessage(), e);
        }
    }

    @McpTool(name = "updateRoute", description = "Writes and commits new detour geometries to a specific vehicle following autonomous negotiation. All coordinates must be in [longitude, latitude] order within the Klang Valley bounding box.")
    public String updateRoute(
            @McpToolParam(description = "Unique identifier of the vehicle (e.g. V-001)", required = true)
            String vehicleId,
            @McpToolParam(description = "Identifier of the route to update (e.g. RT-001)", required = true)
            String routeId,
            @McpToolParam(description = "Ordered list of [longitude, latitude] coordinate pairs representing the new route LineString", required = true)
            List<List<Double>> coordinates,
            @McpToolParam(description = "Optional negotiation rationale or detour explanation", required = false)
            String rationale) {

        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required and cannot be blank");
        }
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required and cannot be blank");
        }
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("coordinates must contain at least 2 points");
        }

        try {
            RouteUpdateResult result = routingService.updateRoute(
                    vehicleId.trim(),
                    routeId.trim(),
                    coordinates,
                    rationale != null ? rationale.trim() : null
            );
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update route: " + e.getMessage(), e);
        }
    }
}
