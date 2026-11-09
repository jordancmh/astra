package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest {

    @Mock
    private GraphHopperClient graphHopperClient;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private NegotiationEventRepository negotiationEventRepository;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(graphHopperClient, routeRepository, negotiationEventRepository);
    }

    @Test
    void updateRoute_shouldPersistRouteAndNegotiationEvent() {
        List<List<Double>> coords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        when(routeRepository.findById("RT-001")).thenReturn(Optional.empty());
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteUpdateResult result = routingService.updateRoute(
                "V-001", "RT-001", coords, 35000.0, 2100000L, "Detour via NPE", "D-001"
        );

        assertThat(result.status()).isEqualTo("COMMITTED");
        assertThat(result.vehicleId()).isEqualTo("V-001");
        assertThat(result.routeId()).isEqualTo("RT-001");
        assertThat(result.coordinatesCount()).isEqualTo(2);
        assertThat(result.distanceMeters()).isEqualTo(35000.0);
        assertThat(result.estimatedTimeMs()).isEqualTo(2100000L);
        assertThat(result.geometry().coordinates()).isEqualTo(coords);

        // Verify Route saved
        ArgumentCaptor<Route> routeCaptor = ArgumentCaptor.forClass(Route.class);
        verify(routeRepository).save(routeCaptor.capture());
        Route savedRoute = routeCaptor.getValue();
        assertThat(savedRoute.getId()).isEqualTo("RT-001");
        assertThat(savedRoute.getStatus()).isEqualTo("OPTIMAL");
        assertThat(savedRoute.getDistanceMeters()).isEqualTo(35000.0);
        assertThat(savedRoute.getEstimatedTimeMs()).isEqualTo(2100000L);
        assertThat(savedRoute.getPathGeometry().getSRID()).isEqualTo(4326);

        // Verify NegotiationEvent saved
        ArgumentCaptor<NegotiationEvent> eventCaptor = ArgumentCaptor.forClass(NegotiationEvent.class);
        verify(negotiationEventRepository).save(eventCaptor.capture());
        NegotiationEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getVehicleId()).isEqualTo("V-001");
        assertThat(savedEvent.getAffectedRoutes()).isEqualTo("RT-001");
        assertThat(savedEvent.getAnomalyId()).isEqualTo("D-001");
        assertThat(savedEvent.getEventMessage()).isEqualTo("Detour via NPE");
    }

    @Test
    void updateRoute_withoutRationale_doesNotSaveNegotiationEvent() {
        List<List<Double>> coords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        when(routeRepository.findById("RT-001")).thenReturn(Optional.empty());
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        routingService.updateRoute("V-001", "RT-001", coords, null);

        verify(routeRepository).save(any(Route.class));
        verify(negotiationEventRepository, never()).save(any(NegotiationEvent.class));
    }

    @Test
    void updateRoute_outOfBoundsCoordinates_throwsIllegalArgumentException() {
        List<List<Double>> invalidCoords = List.of(
                List.of(100.0, 3.1390),
                List.of(101.5540, 3.0755)
        );

        assertThatThrownBy(() -> routingService.updateRoute("V-001", "RT-001", invalidCoords, "test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside Klang Valley");

        verify(routeRepository, never()).save(any());
        verify(negotiationEventRepository, never()).save(any());
    }

    @Test
    void recalculateDetour_withAvoidancePolygon_callsGraphHopperWithCustomModel() {
        List<List<Double>> obstaclePolygon = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        );

        GraphHopperRouteResponse.GraphHopperPath path = new GraphHopperRouteResponse.GraphHopperPath(
                38500.0,
                2300000L,
                new GraphHopperRouteResponse.GraphHopperPoints(
                        "LineString",
                        List.of(List.of(101.4496, 3.0430), List.of(101.5500, 3.0900), List.of(101.7116, 3.1579))
                )
        );

        when(graphHopperClient.routeWithCustomModel(
                eq(101.4496), eq(3.0430), eq(101.7116), eq(3.1579), any(GraphHopperCustomModel.class)
        )).thenReturn(path);

        RouteCalculateResponse response = routingService.recalculateDetour(
                "RT-002", "V-002", 101.4496, 3.0430, 101.7116, 3.1579,
                obstaclePolygon, 0.0, "D-001"
        );

        assertThat(response).isNotNull();
        assertThat(response.routeId()).isEqualTo("RT-002");
        assertThat(response.distanceMeters()).isEqualTo(38500.0);
        assertThat(response.estimatedTimeMs()).isEqualTo(2300000L);
        assertThat(response.geometry().coordinates()).hasSize(3);

        ArgumentCaptor<GraphHopperCustomModel> modelCaptor = ArgumentCaptor.forClass(GraphHopperCustomModel.class);
        verify(graphHopperClient).routeWithCustomModel(
                eq(101.4496), eq(3.0430), eq(101.7116), eq(3.1579), modelCaptor.capture()
        );

        GraphHopperCustomModel capturedModel = modelCaptor.getValue();
        assertThat(capturedModel.speed().get(0).ifCondition()).isEqualTo("in_d_001");
        assertThat(capturedModel.speed().get(0).multiplyBy()).isEqualTo("0");
    }

    @Test
    void recalculateAndCommitDetour_endToEnd_calculatesAndCommitsToDatabase() {
        List<List<Double>> obstaclePolygon = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        );

        GraphHopperRouteResponse.GraphHopperPath path = new GraphHopperRouteResponse.GraphHopperPath(
                40000.0,
                2400000L,
                new GraphHopperRouteResponse.GraphHopperPoints(
                        "LineString",
                        List.of(List.of(101.4496, 3.0430), List.of(101.5500, 3.0900), List.of(101.7116, 3.1579))
                )
        );

        when(graphHopperClient.routeWithCustomModel(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(GraphHopperCustomModel.class)
        )).thenReturn(path);
        when(routeRepository.findById("RT-003")).thenReturn(Optional.empty());
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RouteUpdateResult result = routingService.recalculateAndCommitDetour(
                "V-003", "RT-003", 101.4496, 3.0430, 101.7116, 3.1579,
                obstaclePolygon, 0.0, "Detour avoiding D-002", "D-002"
        );

        assertThat(result.status()).isEqualTo("COMMITTED");
        assertThat(result.vehicleId()).isEqualTo("V-003");
        assertThat(result.routeId()).isEqualTo("RT-003");
        assertThat(result.distanceMeters()).isEqualTo(40000.0);
        assertThat(result.estimatedTimeMs()).isEqualTo(2400000L);

        verify(routeRepository).save(any(Route.class));
        verify(negotiationEventRepository).save(any(NegotiationEvent.class));
    }
}
