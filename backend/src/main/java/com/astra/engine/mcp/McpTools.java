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
            String rationale,
            @McpToolParam(description = "Optional total route distance in meters", required = false)
            Double distanceMeters,
            @McpToolParam(description = "Optional estimated travel time in milliseconds", required = false)
            Long estimatedTimeMs,
            @McpToolParam(description = "Optional associated anomaly ID triggering the negotiation (e.g. D-001)", required = false)
            String anomalyId) {

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
            RouteUpdateResult result;
            if (distanceMeters != null || estimatedTimeMs != null || (anomalyId != null && !anomalyId.isBlank())) {
                result = routingService.updateRoute(
                        vehicleId.trim(),
                        routeId.trim(),
                        coordinates,
                        distanceMeters,
                        estimatedTimeMs,
                        rationale != null ? rationale.trim() : null,
                        anomalyId != null ? anomalyId.trim() : null
                );
            } else {
                result = routingService.updateRoute(
                        vehicleId.trim(),
                        routeId.trim(),
                        coordinates,
                        rationale != null ? rationale.trim() : null
                );
            }
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to update route: " + e.getMessage(), e);
        }
    }

    public String updateRoute(String vehicleId, String routeId, List<List<Double>> coordinates, String rationale) {
        return updateRoute(vehicleId, routeId, coordinates, rationale, null, null, null);
    }

    @McpTool(name = "recalculateDetour", description = "Dynamically recalculates a vehicle detour around an anomaly using GraphHopper obstacle avoidance and commits the route to PostGIS.")
    public String recalculateDetour(
            @McpToolParam(description = "Unique identifier of the vehicle (e.g. V-001)", required = true)
            String vehicleId,
            @McpToolParam(description = "Identifier of the route to update (e.g. RT-001)", required = true)
            String routeId,
            @McpToolParam(description = "Origin longitude (101.40 to 101.90)", required = true)
            Double originLng,
            @McpToolParam(description = "Origin latitude (2.90 to 3.30)", required = true)
            Double originLat,
            @McpToolParam(description = "Destination longitude (101.40 to 101.90)", required = true)
            Double destLng,
            @McpToolParam(description = "Destination latitude (2.90 to 3.30)", required = true)
            Double destLat,
            @McpToolParam(description = "Obstacle polygon coordinates [[lng, lat], ...] to avoid", required = false)
            List<List<Double>> avoidPolygon,
            @McpToolParam(description = "Speed multiplier penalty (0.0 for closure, 0.2 for congestion)", required = false)
            Double penaltyFactor,
            @McpToolParam(description = "Negotiation rationale or detour explanation", required = false)
            String rationale,
            @McpToolParam(description = "Associated anomaly ID (e.g. D-001)", required = false)
            String anomalyId) {

        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required and cannot be blank");
        }
        if (routeId == null || routeId.isBlank()) {
            throw new IllegalArgumentException("routeId is required and cannot be blank");
        }
        if (originLng == null || originLat == null || destLng == null || destLat == null) {
            throw new IllegalArgumentException("origin and destination coordinates are required");
        }

        try {
            double penalty = penaltyFactor != null ? penaltyFactor : 0.0;
            RouteUpdateResult result = routingService.recalculateAndCommitDetour(
                    vehicleId.trim(),
                    routeId.trim(),
                    originLng,
                    originLat,
                    destLng,
                    destLat,
                    avoidPolygon != null ? avoidPolygon : List.of(),
                    penalty,
                    rationale != null ? rationale.trim() : null,
                    anomalyId != null ? anomalyId.trim() : null
            );
            return objectMapper.writeValueAsString(result);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to recalculate and commit detour: " + e.getMessage(), e);
        }
    }
}
