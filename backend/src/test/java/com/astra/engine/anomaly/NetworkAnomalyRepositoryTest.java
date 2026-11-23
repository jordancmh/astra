package com.astra.engine.anomaly;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NetworkAnomalyRepositoryTest {

    @Autowired
    private NetworkAnomalyRepository repository;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void saveAndFindById_persistsNetworkAnomalyWithSpatialTypes() {
        Point center = GeometryUtils.createPoint(101.6210, 3.1180);

        Coordinate[] ring = new Coordinate[]{
                new Coordinate(101.615, 3.112),
                new Coordinate(101.627, 3.112),
                new Coordinate(101.627, 3.124),
                new Coordinate(101.615, 3.124),
                new Coordinate(101.615, 3.112)
        };
        Polygon bbox = GF.createPolygon(ring);

        NetworkAnomaly anomaly = new NetworkAnomaly();
        anomaly.setId("D-001");
        anomaly.setAnomalyType("ROAD_CLOSURE");
        anomaly.setSeverity("HIGH");
        anomaly.setCenterPoint(center);
        anomaly.setBoundingBox(bbox);
        anomaly.setRadiusMeters(600.0);
        anomaly.setActive(true);
        anomaly.setInjectedAt(Instant.now());

        NetworkAnomaly saved = repository.save(anomaly);

        assertThat(saved.getId()).isEqualTo("D-001");

        Optional<NetworkAnomaly> found = repository.findById("D-001");
        assertThat(found).isPresent();
        assertThat(found.get().getAnomalyType()).isEqualTo("ROAD_CLOSURE");
        assertThat(found.get().getSeverity()).isEqualTo("HIGH");
        assertThat(found.get().getRadiusMeters()).isEqualTo(600.0);
        assertThat(found.get().isActive()).isTrue();
        assertThat(found.get().getCenterPoint().getSRID()).isEqualTo(4326);
        assertThat(found.get().getCenterPoint().getX()).isEqualTo(101.6210);
        assertThat(found.get().getCenterPoint().getY()).isEqualTo(3.1180);
        assertThat(found.get().getBoundingBox().getSRID()).isEqualTo(4326);
    }

    @Test
    void findByIsActiveTrueOrderByInjectedAtDesc_returnsOnlyActiveAnomaliesSorted() {
        Point center = GeometryUtils.createPoint(101.6210, 3.1180);
        Coordinate[] ring = new Coordinate[]{
                new Coordinate(101.615, 3.112),
                new Coordinate(101.627, 3.112),
                new Coordinate(101.627, 3.124),
                new Coordinate(101.615, 3.124),
                new Coordinate(101.615, 3.112)
        };
        Polygon bbox = GF.createPolygon(ring);

        NetworkAnomaly a1 = new NetworkAnomaly(
                "D-TEST-01", "CONGESTION", "MEDIUM", center, bbox, 400.0, true, Instant.now().minusSeconds(60)
        );
        NetworkAnomaly a2 = new NetworkAnomaly(
                "D-TEST-02", "ACCIDENT", "CRITICAL", center, bbox, 500.0, true, Instant.now()
        );
        NetworkAnomaly inactive = new NetworkAnomaly(
                "D-TEST-03", "WEATHER", "LOW", center, bbox, 300.0, false, Instant.now()
        );

        repository.saveAll(List.of(a1, a2, inactive));

        List<NetworkAnomaly> activeList = repository.findByIsActiveTrueOrderByInjectedAtDesc();
        assertThat(activeList).isNotEmpty();
        List<String> ids = activeList.stream().map(NetworkAnomaly::getId).toList();
        assertThat(ids).contains("D-TEST-02", "D-TEST-01");
        assertThat(ids).doesNotContain("D-TEST-03");

        // Verify descending order
        int idx2 = ids.indexOf("D-TEST-02");
        int idx1 = ids.indexOf("D-TEST-01");
        assertThat(idx2).isLessThan(idx1);
    }
}
