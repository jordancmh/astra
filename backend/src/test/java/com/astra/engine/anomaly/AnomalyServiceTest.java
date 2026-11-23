package com.astra.engine.anomaly;

import com.astra.engine.anomaly.dto.AnomalyClearResponse;
import com.astra.engine.anomaly.dto.AnomalyDto;
import com.astra.engine.anomaly.dto.AnomalyInjectRequest;
import com.astra.engine.anomaly.dto.AnomalyInjectResponse;
import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.RoutingService;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.VehicleStatusDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.locationtech.jts.geom.Polygon;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalyServiceTest {

    @Mock
    private NetworkAnomalyRepository repository;

    @Mock
    private RoutingService routingService;

    @Mock
    private TelemetryService telemetryService;

    private AnomalyService anomalyService;

    @BeforeEach
    void setUp() {
        anomalyService = new AnomalyService(repository, routingService, telemetryService);
    }

    @Test
    void injectAnomaly_withinKlangValley_persistsAnomalyAndDetectsIntersections() {
        when(repository.findAll()).thenReturn(List.of());
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE",
                "HIGH",
                3.1180,
                101.6210,
                600.0
        );

        when(routingService.findIntersectingRouteIds(any())).thenReturn(List.of("RT-003"));
        when(telemetryService.getFleetState(null)).thenReturn(List.of(
                new VehicleStatusDto("V-003", "RT-003", 3.1180, 101.6210, 90.0, 60.0, "NOMINAL", Instant.now()),
                new VehicleStatusDto("V-004", "RT-099", 3.1400, 101.6800, 45.0, 50.0, "NOMINAL", Instant.now())
        ));

        when(repository.save(any(NetworkAnomaly.class))).thenAnswer(inv -> inv.getArgument(0));

        AnomalyInjectResponse response = anomalyService.injectAnomaly(request);

        assertThat(response.anomalyId()).startsWith("D-");
        assertThat(response.anomalyType()).isEqualTo("ROAD_CLOSURE");
        assertThat(response.severity()).isEqualTo("HIGH");
        assertThat(response.affectedRouteIds()).containsExactly("RT-003");
        assertThat(response.affectedVehicleIds()).containsExactly("V-003");

        ArgumentCaptor<NetworkAnomaly> captor = ArgumentCaptor.forClass(NetworkAnomaly.class);
        verify(repository).save(captor.capture());
        NetworkAnomaly saved = captor.getValue();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getCenterPoint().getX()).isEqualTo(101.6210);
        assertThat(saved.getCenterPoint().getY()).isEqualTo(3.1180);
        assertThat(saved.getBoundingBox()).isNotNull();
        assertThat(saved.getBoundingBox().getSRID()).isEqualTo(4326);
    }

    @Test
    void injectAnomaly_outOfBounds_throwsIllegalArgumentException() {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE",
                "HIGH",
                5.0000, // Out of Klang Valley
                101.6210,
                600.0
        );

        assertThatThrownBy(() -> anomalyService.injectAnomaly(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside Klang Valley");
    }

    @Test
    void injectAnomaly_nonPositiveRadius_throwsIllegalArgumentException() {
        AnomalyInjectRequest request = new AnomalyInjectRequest(
                "ROAD_CLOSURE",
                "HIGH",
                3.1180,
                101.6210,
                0.0
        );

        assertThatThrownBy(() -> anomalyService.injectAnomaly(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("radiusMeters must be positive");
    }

    @Test
    void getActiveAnomalies_returnsFormattedDtos() {
        Polygon bbox = GeometryUtils.createGeodesicCirclePolygon(101.6210, 3.1180, 500.0);
        NetworkAnomaly anomaly = new NetworkAnomaly(
                "D-001", "CONGESTION", "MEDIUM",
                GeometryUtils.createPoint(101.6210, 3.1180),
                bbox, 500.0, true, Instant.now()
        );

        when(repository.findByIsActiveTrueOrderByInjectedAtDesc()).thenReturn(List.of(anomaly));

        List<AnomalyDto> dtos = anomalyService.getActiveAnomalies();

        assertThat(dtos).hasSize(1);
        AnomalyDto dto = dtos.get(0);
        assertThat(dto.id()).isEqualTo("D-001");
        assertThat(dto.anomalyType()).isEqualTo("CONGESTION");
        assertThat(dto.center()).containsExactly(101.6210, 3.1180);
        assertThat(dto.polygon()).isNotNull();
        assertThat(dto.polygon().type()).isEqualTo("Polygon");
        assertThat(dto.polygon().coordinates().get(0)).isNotEmpty();
    }

    @Test
    void deleteAnomaly_existingId_deactivatesAnomaly() {
        NetworkAnomaly anomaly = new NetworkAnomaly(
                "D-001", "ROAD_CLOSURE", "HIGH",
                GeometryUtils.createPoint(101.6210, 3.1180),
                null, 500.0, true, Instant.now()
        );

        when(repository.findById("D-001")).thenReturn(Optional.of(anomaly));

        boolean deleted = anomalyService.deleteAnomaly("D-001");

        assertThat(deleted).isTrue();
        assertThat(anomaly.isActive()).isFalse();
        verify(repository).save(anomaly);
    }

    @Test
    void deleteAnomaly_nonExistingId_returnsFalse() {
        when(repository.findById("D-999")).thenReturn(Optional.empty());

        boolean deleted = anomalyService.deleteAnomaly("D-999");

        assertThat(deleted).isFalse();
    }

    @Test
    void clearAllAnomalies_deactivatesAllActiveAnomalies() {
        NetworkAnomaly a1 = new NetworkAnomaly("D-001", "ROAD_CLOSURE", "HIGH", null, null, 500.0, true, Instant.now());
        NetworkAnomaly a2 = new NetworkAnomaly("D-002", "ACCIDENT", "CRITICAL", null, null, 600.0, true, Instant.now());

        when(repository.findByIsActiveTrueOrderByInjectedAtDesc()).thenReturn(List.of(a1, a2));

        AnomalyClearResponse response = anomalyService.clearAllAnomalies();

        assertThat(response.clearedCount()).isEqualTo(2);
        assertThat(a1.isActive()).isFalse();
        assertThat(a2.isActive()).isFalse();
        verify(repository).saveAll(List.of(a1, a2));
    }
}
