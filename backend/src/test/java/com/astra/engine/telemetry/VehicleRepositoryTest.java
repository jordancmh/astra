package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VehicleRepositoryTest {

    @Autowired
    private VehicleRepository repository;

    @Test
    void saveAndFindById_shouldPersistAndRetrieveVehicle() {
        Point location = GeometryUtils.createPoint(101.5833, 3.0833);

        Vehicle vehicle = new Vehicle(
                "V-001",
                "NOMINAL",
                "RT-001",
                location,
                87.0,
                60.0,
                5
        );

        Vehicle saved = repository.save(vehicle);

        assertThat(saved.getId()).isEqualTo("V-001");
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Vehicle> found = repository.findById("V-001");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo("NOMINAL");
        assertThat(found.get().getCurrentRouteId()).isEqualTo("RT-001");
        assertThat(found.get().getCargoPriority()).isEqualTo(5);
        assertThat(found.get().getCurrentLocation().getSRID()).isEqualTo(4326);
        assertThat(found.get().getCurrentLocation().getX()).isEqualTo(101.5833);
        assertThat(found.get().getCurrentLocation().getY()).isEqualTo(3.0833);
    }

    @Test
    void findAllByOrderByCargoPriorityDesc_shouldReturnSortedList() {
        Point p1 = GeometryUtils.createPoint(101.58, 3.08);
        Point p2 = GeometryUtils.createPoint(101.60, 3.10);

        repository.save(new Vehicle("V-LOW", "NOMINAL", "RT-LOW", p1, 0.0, 50.0, 1));
        repository.save(new Vehicle("V-HIGH", "NOMINAL", "RT-HIGH", p2, 0.0, 50.0, 5));

        List<Vehicle> list = repository.findAllByOrderByCargoPriorityDesc();

        assertThat(list).isNotEmpty();
        assertThat(list.get(0).getCargoPriority()).isGreaterThanOrEqualTo(list.get(list.size() - 1).getCargoPriority());
    }
}
