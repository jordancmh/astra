package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VehicleTelemetryRepositoryTest {

    @Autowired
    private VehicleTelemetryRepository repository;

    @Test
    void saveAndFindById_shouldPersistAndRetrieveTelemetryWithPostGisPoint() {
        double lng = 101.6371;
        double lat = 3.1140;
        Point point = GeometryUtils.createPoint(lng, lat);

        VehicleTelemetry telemetry = new VehicleTelemetry();
        telemetry.setVehicleId("V-001");
        telemetry.setRouteId("RT-001");
        telemetry.setCoordinates(point);
        telemetry.setBearing(87.0);
        telemetry.setSpeedKmh(78.0);
        // recordedAt is null to test @PrePersist

        VehicleTelemetry saved = repository.save(telemetry);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRecordedAt()).isNotNull();

        Optional<VehicleTelemetry> found = repository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getVehicleId()).isEqualTo("V-001");
        assertThat(found.get().getRouteId()).isEqualTo("RT-001");
        assertThat(found.get().getCoordinates().getSRID()).isEqualTo(4326);
        assertThat(found.get().getCoordinates().getX()).isEqualTo(lng);
        assertThat(found.get().getCoordinates().getY()).isEqualTo(lat);
        assertThat(found.get().getBearing()).isEqualTo(87.0);
        assertThat(found.get().getSpeedKmh()).isEqualTo(78.0);
    }

    @Test
    void findByVehicleIdOrderByRecordedAtDesc_shouldReturnSortedList() {
        Point point = GeometryUtils.createPoint(101.6371, 3.1140);

        VehicleTelemetry t1 = new VehicleTelemetry();
        t1.setVehicleId("V-002");
        t1.setCoordinates(point);
        t1.setRecordedAt(Instant.now().minusSeconds(60));
        repository.save(t1);

        VehicleTelemetry t2 = new VehicleTelemetry();
        t2.setVehicleId("V-002");
        t2.setCoordinates(point);
        t2.setRecordedAt(Instant.now());
        repository.save(t2);

        List<VehicleTelemetry> logs = repository.findByVehicleIdOrderByRecordedAtDesc("V-002");
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).getRecordedAt()).isAfter(logs.get(1).getRecordedAt());
    }
}
