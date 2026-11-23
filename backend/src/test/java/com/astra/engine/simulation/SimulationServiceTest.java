package com.astra.engine.simulation;

import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.GraphHopperClient;
import com.astra.engine.routing.Route;
import com.astra.engine.routing.RouteRepository;
import com.astra.engine.simulation.dto.SimulationStatusResponse;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.Vehicle;
import com.astra.engine.telemetry.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimulationServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private TelemetryService telemetryService;

    @Mock
    private GraphHopperClient graphHopperClient;

    private SimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new SimulationService(vehicleRepository, routeRepository, telemetryService, graphHopperClient);
    }

    @Test
    void isInactiveByDefault_shouldReturnRunningFalse() {
        when(vehicleRepository.count()).thenReturn(0L);
        SimulationStatusResponse status = simulationService.getStatus();
        assertThat(status.running()).isFalse();
        assertThat(status.vehicleCount()).isEqualTo(0);
    }

    @Test
    void seedFleet_shouldCreate5VehiclesAcrossCorridors() {
        // GraphHopper throws or returns null -> triggers deterministic fallback
        when(graphHopperClient.routeWithWaypoints(any())).thenReturn(null);

        SimulationStatusResponse response = simulationService.seedFleet();

        assertThat(response.vehicleCount()).isEqualTo(5);
        verify(vehicleRepository, atLeastOnce()).save(any(Vehicle.class));
        verify(routeRepository, atLeastOnce()).save(any(Route.class));
        verify(telemetryService, atLeastOnce()).ingest(any());
    }

    @Test
    void tick_advancesVehicleCoordinatesAndUpdatesBearing() {
        Point loc1 = GeometryUtils.createPoint(101.55, 3.05);
        LineString path = GeometryUtils.createLineString(List.of(
                List.of(101.55, 3.05),
                List.of(101.58, 3.08),
                List.of(101.60, 3.10)
        ));

        Vehicle v1 = new Vehicle("V-001", "NOMINAL", "RT-001", loc1, 0.0, 50.0, 5);
        Route r1 = new Route("RT-001", "OPTIMAL", loc1, loc1, path, 5000.0, 300000L);

        when(vehicleRepository.findAll()).thenReturn(List.of(v1));
        when(routeRepository.findById("RT-001")).thenReturn(Optional.of(r1));

        simulationService.tick();

        assertThat(v1.getCurrentLocation().getX()).isEqualTo(101.58);
        assertThat(v1.getCurrentLocation().getY()).isEqualTo(3.08);
        assertThat(v1.getBearing()).isGreaterThan(0.0);
        verify(vehicleRepository).save(v1);
        verify(telemetryService).ingest(any());
    }
}
