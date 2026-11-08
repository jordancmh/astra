package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock
    private VehicleTelemetryRepository telemetryRepository;

    private TelemetryService telemetryService;

    @BeforeEach
    void setUp() {
        telemetryService = new TelemetryService(telemetryRepository);
    }

    @Test
    void getFleetState_withVehicleId_shouldReturnSpecificVehicle() {
        VehicleTelemetry entity = new VehicleTelemetry(
                "V-001",
                "RT-001",
                GeometryUtils.createPoint(101.6371, 3.1140),
                87.0,
                78.0,
                Instant.now()
        );

        when(telemetryRepository.findFirstByVehicleIdOrderByRecordedAtDesc("V-001"))
                .thenReturn(Optional.of(entity));

        List<VehicleStatusDto> result = telemetryService.getFleetState("V-001");

        assertThat(result).hasSize(1);
        VehicleStatusDto dto = result.get(0);
        assertThat(dto.vehicleId()).isEqualTo("V-001");
        assertThat(dto.routeId()).isEqualTo("RT-001");
        assertThat(dto.longitude()).isEqualTo(101.6371);
        assertThat(dto.latitude()).isEqualTo(3.1140);
        assertThat(dto.status()).isEqualTo("NOMINAL");
        verify(telemetryRepository).findFirstByVehicleIdOrderByRecordedAtDesc("V-001");
    }

    @Test
    void getFleetState_withoutVehicleId_shouldReturnAllVehicles() {
        VehicleTelemetry entity1 = new VehicleTelemetry(
                "V-001",
                "RT-001",
                GeometryUtils.createPoint(101.6371, 3.1140),
                87.0,
                78.0,
                Instant.now()
        );
        VehicleTelemetry entity2 = new VehicleTelemetry(
                "V-002",
                "RT-002",
                GeometryUtils.createPoint(101.5540, 3.0755),
                45.0,
                60.0,
                Instant.now()
        );

        when(telemetryRepository.findLatestTelemetryForAllVehicles())
                .thenReturn(List.of(entity1, entity2));

        List<VehicleStatusDto> result = telemetryService.getFleetState(null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).vehicleId()).isEqualTo("V-001");
        assertThat(result.get(1).vehicleId()).isEqualTo("V-002");
        verify(telemetryRepository).findLatestTelemetryForAllVehicles();
    }
}
