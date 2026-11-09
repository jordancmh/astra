package com.astra.engine.mcp;

import com.astra.engine.common.GeoJsonLineString;
import com.astra.engine.routing.RouteUpdateResult;
import com.astra.engine.routing.RoutingService;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.VehicleStatusDto;
import io.modelcontextprotocol.server.McpServerFeatures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolsTest {

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private RoutingService routingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private McpTools mcpTools;

    @BeforeEach
    void setUp() {
        mcpTools = new McpTools(telemetryService, routingService, objectMapper);
    }

    @Test
    void getFleetState_withoutVehicleId_returnsAllVehiclesJson() {
        VehicleStatusDto v1 = new VehicleStatusDto("V-001", "RT-001", 3.1140, 101.6371, 87.0, 78.0, "NOMINAL", Instant.now());
        VehicleStatusDto v2 = new VehicleStatusDto("V-002", "RT-002", 3.0755, 101.5540, 45.0, 60.0, "NOMINAL", Instant.now());
        when(telemetryService.getFleetState(null)).thenReturn(List.of(v1, v2));

        String json = mcpTools.getFleetState(null);

        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"count\":2");
        assertThat(json).contains("V-001");
        assertThat(json).contains("V-002");
        verify(telemetryService).getFleetState(null);
    }

    @Test
    void getFleetState_withVehicleId_returnsFilteredVehicleJson() {
        VehicleStatusDto v1 = new VehicleStatusDto("V-001", "RT-001", 3.1140, 101.6371, 87.0, 78.0, "NOMINAL", Instant.now());
        when(telemetryService.getFleetState("V-001")).thenReturn(List.of(v1));

        String json = mcpTools.getFleetState("V-001");

        assertThat(json).contains("\"status\":\"SUCCESS\"");
        assertThat(json).contains("\"count\":1");
        assertThat(json).contains("V-001");
        verify(telemetryService).getFleetState("V-001");
    }

    @Test
    void updateRoute_withValidKlangValleyCoordinates_returnsCommittedResult() {
        List<List<Double>> validCoords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        RouteUpdateResult updateResult = new RouteUpdateResult(
                "COMMITTED",
                "V-001",
                "RT-001",
                2,
                new GeoJsonLineString(validCoords),
                Instant.now()
        );
        when(routingService.updateRoute(eq("V-001"), eq("RT-001"), eq(validCoords), eq("Detour via Federal Hwy")))
                .thenReturn(updateResult);

        String json = mcpTools.updateRoute("V-001", "RT-001", validCoords, "Detour via Federal Hwy");

        assertThat(json).contains("\"status\":\"COMMITTED\"");
        assertThat(json).contains("\"vehicleId\":\"V-001\"");
        assertThat(json).contains("\"routeId\":\"RT-001\"");
        assertThat(json).contains("\"coordinatesCount\":2");
        verify(routingService).updateRoute("V-001", "RT-001", validCoords, "Detour via Federal Hwy");
    }

    @Test
    void updateRoute_withExtendedMetricsAndAnomalyId_callsOverloadedServiceMethod() {
        List<List<Double>> validCoords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        RouteUpdateResult updateResult = new RouteUpdateResult(
                "COMMITTED",
                "V-001",
                "RT-001",
                2,
                new GeoJsonLineString(validCoords),
                35000.0,
                2100000L,
                Instant.now()
        );
        when(routingService.updateRoute(
                eq("V-001"), eq("RT-001"), eq(validCoords), eq(35000.0), eq(2100000L),
                eq("Detour rationale"), eq("D-001")
        )).thenReturn(updateResult);

        String json = mcpTools.updateRoute(
                "V-001", "RT-001", validCoords, "Detour rationale", 35000.0, 2100000L, "D-001"
        );

        assertThat(json).contains("\"status\":\"COMMITTED\"");
        assertThat(json).contains("\"distanceMeters\":35000.0");
        assertThat(json).contains("\"estimatedTimeMs\":2100000");
        verify(routingService).updateRoute(
                eq("V-001"), eq("RT-001"), eq(validCoords), eq(35000.0), eq(2100000L),
                eq("Detour rationale"), eq("D-001")
        );
    }

    @Test
    void recalculateDetour_withValidParameters_returnsCommittedDetourResult() {
        List<List<Double>> avoidPolygon = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        );

        RouteUpdateResult detourResult = new RouteUpdateResult(
                "COMMITTED",
                "V-004",
                "RT-004",
                3,
                new GeoJsonLineString(List.of(List.of(101.4496, 3.0430), List.of(101.5500, 3.0900), List.of(101.7116, 3.1579))),
                41200.0,
                2450000L,
                Instant.now()
        );

        when(routingService.recalculateAndCommitDetour(
                eq("V-004"), eq("RT-004"), eq(101.4496), eq(3.0430), eq(101.7116), eq(3.1579),
                eq(avoidPolygon), eq(0.0), eq("Circumnavigate closure D-001"), eq("D-001")
        )).thenReturn(detourResult);

        String json = mcpTools.recalculateDetour(
                "V-004", "RT-004", 101.4496, 3.0430, 101.7116, 3.1579,
                avoidPolygon, 0.0, "Circumnavigate closure D-001", "D-001"
        );

        assertThat(json).contains("\"status\":\"COMMITTED\"");
        assertThat(json).contains("\"vehicleId\":\"V-004\"");
        assertThat(json).contains("\"distanceMeters\":41200.0");
        verify(routingService).recalculateAndCommitDetour(
                eq("V-004"), eq("RT-004"), eq(101.4496), eq(3.0430), eq(101.7116), eq(3.1579),
                eq(avoidPolygon), eq(0.0), eq("Circumnavigate closure D-001"), eq("D-001")
        );
    }

    @Test
    void updateRoute_withOutOfBoundsCoordinates_throwsIllegalArgumentException() {
        List<List<Double>> outOfBoundsCoords = List.of(
                List.of(101.6839, 3.1390),
                List.of(105.0000, 5.0000) // Far outside Klang Valley
        );

        when(routingService.updateRoute(eq("V-001"), eq("RT-001"), eq(outOfBoundsCoords), any()))
                .thenThrow(new IllegalArgumentException("Coordinate [105.000000, 5.000000] is outside Klang Valley bounding box"));

        assertThatThrownBy(() -> mcpTools.updateRoute("V-001", "RT-001", outOfBoundsCoords, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside Klang Valley bounding box");
    }

    @Test
    void updateRoute_withLessThanTwoCoordinates_throwsIllegalArgumentException() {
        List<List<Double>> singlePoint = List.of(List.of(101.6839, 3.1390));

        assertThatThrownBy(() -> mcpTools.updateRoute("V-001", "RT-001", singlePoint, "Invalid single point"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinates must contain at least 2 points");

        assertThatThrownBy(() -> mcpTools.updateRoute("V-001", "RT-001", null, "Null points"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinates must contain at least 2 points");
    }

    @Test
    void updateRoute_withBlankIdentifiers_throwsIllegalArgumentException() {
        List<List<Double>> coords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        assertThatThrownBy(() -> mcpTools.updateRoute("", "RT-001", coords, "Blank vehicleId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("vehicleId is required and cannot be blank");

        assertThatThrownBy(() -> mcpTools.updateRoute("V-001", "   ", coords, "Blank routeId"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routeId is required and cannot be blank");
    }

    @Test
    void updateRoute_securityNfr21_rawSqlInputTreatedAsLiteralString() {
        List<List<Double>> validCoords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        String maliciousRationale = "'; DROP TABLE route; --";
        RouteUpdateResult updateResult = new RouteUpdateResult(
                "COMMITTED",
                "V-001",
                "RT-001",
                2,
                new GeoJsonLineString(validCoords),
                Instant.now()
        );

        when(routingService.updateRoute("V-001", "RT-001", validCoords, maliciousRationale))
                .thenReturn(updateResult);

        String json = mcpTools.updateRoute("V-001", "RT-001", validCoords, maliciousRationale);

        assertThat(json).contains("\"status\":\"COMMITTED\"");
        verify(routingService).updateRoute("V-001", "RT-001", validCoords, maliciousRationale);
    }

    @Test
    void mcpTools_shouldProduceThreeMcpToolSpecifications() {
        List<McpServerFeatures.SyncToolSpecification> toolSpecs =
                org.springframework.ai.mcp.annotation.spring.SyncMcpAnnotationProviders.toolSpecifications(List.of(mcpTools));

        assertThat(toolSpecs).hasSize(3);
        List<String> toolNames = toolSpecs.stream().map(s -> s.tool().name()).toList();
        assertThat(toolNames).containsExactlyInAnyOrder("getFleetState", "updateRoute", "recalculateDetour");
    }
}
