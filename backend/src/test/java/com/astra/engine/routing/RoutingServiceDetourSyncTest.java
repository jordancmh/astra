package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import com.astra.engine.common.GeometryUtils;
import com.astra.engine.telemetry.Vehicle;
import com.astra.engine.telemetry.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingServiceDetourSyncTest {

    @Mock
    private GraphHopperClient graphHopperClient;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private NegotiationEventRepository negotiationEventRepository;

    @Mock
    private VehicleRepository vehicleRepository;

    private RoutingService routingService;

    @BeforeEach
    void setUp() {
        routingService = new RoutingService(
                graphHopperClient,
                routeRepository,
                negotiationEventRepository,
                vehicleRepository
        );
    }

    @Test
    void updateRoute_withVehicleId_updatesVehicleCurrentRouteAndReroutingStatus() {
        String vehicleId = "V-001";
        String routeId = "RT-001";
        List<List<Double>> coords = List.of(
                List.of(101.5270, 3.0730),
                List.of(101.5833, 3.0833),
                List.of(101.7116, 3.1579)
        );

        Route mockRoute = new Route();
        mockRoute.setId(routeId);
        mockRoute.setPathGeometry(GeometryUtils.createLineString(coords));
        when(routeRepository.findById(routeId)).thenReturn(Optional.of(mockRoute));
        when(routeRepository.save(any(Route.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Vehicle mockVehicle = new Vehicle();
        mockVehicle.setId(vehicleId);
        mockVehicle.setStatus("DISRUPTED");
        mockVehicle.setCurrentRouteId("RT-OLD");
        when(vehicleRepository.findById(vehicleId)).thenReturn(Optional.of(mockVehicle));

        RouteUpdateResult result = routingService.updateRoute(
                vehicleId,
                routeId,
                coords,
                15000.0,
                900000L,
                "Detour assigned avoiding Federal Highway disruption",
                "D-001"
        );

        assertThat(result.status()).isEqualTo("COMMITTED");

        ArgumentCaptor<Vehicle> vehicleCaptor = ArgumentCaptor.forClass(Vehicle.class);
        verify(vehicleRepository).save(vehicleCaptor.capture());
        Vehicle updatedVehicle = vehicleCaptor.getValue();

        assertThat(updatedVehicle.getCurrentRouteId()).isEqualTo(routeId);
        assertThat(updatedVehicle.getStatus()).isEqualTo("REROUTING");
        assertThat(updatedVehicle.getUpdatedAt()).isNotNull();

        verify(negotiationEventRepository).save(any(NegotiationEvent.class));
    }
}
