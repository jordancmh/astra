package com.astra.engine.telemetry;

import com.astra.engine.anomaly.NetworkAnomaly;
import com.astra.engine.anomaly.NetworkAnomalyRepository;
import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.Route;
import com.astra.engine.routing.RouteRepository;
import com.astra.engine.telemetry.dto.FleetStateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FleetServiceTest {

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private RouteRepository routeRepository;

    @Mock
    private NetworkAnomalyRepository anomalyRepository;

    private FleetService fleetService;

    @BeforeEach
    void setUp() {
        fleetService = new FleetService(vehicleRepository, routeRepository, anomalyRepository);
    }

    @Test
    void getFleetState_nominalFleet_returnsNominalVehiclesAndSummary() {
        Point loc = GeometryUtils.createPoint(101.58, 3.08);
        LineString path = GeometryUtils.createLineString(List.of(
                List.of(101.55, 3.05),
                List.of(101.60, 3.10)
        ));

        Vehicle v1 = new Vehicle("V-001", "NOMINAL", "RT-001", loc, 90.0, 60.0, 5);
        Route r1 = new Route("RT-001", "OPTIMAL", loc, loc, path, 5000.0, 300000L);

        when(vehicleRepository.findAllByOrderByCargoPriorityDesc()).thenReturn(List.of(v1));
        when(routeRepository.findAll()).thenReturn(List.of(r1));
        when(anomalyRepository.findByIsActiveTrueOrderByInjectedAtDesc()).thenReturn(List.of());

        FleetStateResponse state = fleetService.getFleetState();

        assertThat(state.summary().total()).isEqualTo(1);
        assertThat(state.summary().nominal()).isEqualTo(1);
        assertThat(state.summary().disrupted()).isEqualTo(0);
        assertThat(state.vehicles().get(0).status()).isEqualTo("NOMINAL");
        assertThat(state.vehicles().get(0).intersectedAnomalyId()).isNull();
    }

    @Test
    void getFleetState_whenRouteIntersectsActiveAnomaly_flagsVehicleAsDisrupted() {
        Point loc = GeometryUtils.createPoint(101.58, 3.08);
        LineString path = GeometryUtils.createLineString(List.of(
                List.of(101.50, 3.00),
                List.of(101.65, 3.15)
        ));

        Vehicle v1 = new Vehicle("V-001", "NOMINAL", "RT-001", loc, 90.0, 60.0, 5);
        Route r1 = new Route("RT-001", "OPTIMAL", loc, loc, path, 15000.0, 600000L);

        Point center = GeometryUtils.createPoint(101.58, 3.08);
        Polygon hazardBox = GeometryUtils.createGeodesicCirclePolygon(101.58, 3.08, 1000.0);
        NetworkAnomaly anomaly = new NetworkAnomaly("D-001", "ROAD_CLOSURE", "HIGH", center, hazardBox, 1000.0, true, Instant.now());

        when(vehicleRepository.findAllByOrderByCargoPriorityDesc()).thenReturn(List.of(v1));
        when(routeRepository.findAll()).thenReturn(List.of(r1));
        when(anomalyRepository.findByIsActiveTrueOrderByInjectedAtDesc()).thenReturn(List.of(anomaly));

        FleetStateResponse state = fleetService.getFleetState();

        assertThat(state.summary().total()).isEqualTo(1);
        assertThat(state.summary().disrupted()).isEqualTo(1);
        assertThat(state.summary().nominal()).isEqualTo(0);
        assertThat(state.vehicles().get(0).status()).isEqualTo("DISRUPTED");
        assertThat(state.vehicles().get(0).intersectedAnomalyId()).isEqualTo("D-001");
    }
}
