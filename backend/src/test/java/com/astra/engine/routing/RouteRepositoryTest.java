package com.astra.engine.routing;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RouteRepositoryTest {

    @Autowired
    private RouteRepository routeRepository;

    private static final GeometryFactory GF = new GeometryFactory(new PrecisionModel(), 4326);

    @Test
    void findRoutesIntersecting_returnsOnlyRoutesIntersectingGeometry() {
        // Route 1 crosses (101.6200, 3.1150)
        Point origin1 = GeometryUtils.createPoint(101.6000, 3.1000);
        Point dest1 = GeometryUtils.createPoint(101.6500, 3.1300);
        LineString path1 = GeometryUtils.createLineString(List.of(
                List.of(101.6000, 3.1000),
                List.of(101.6200, 3.1150),
                List.of(101.6500, 3.1300)
        ));
        Route r1 = new Route("RT-INT-01", "OPTIMAL", origin1, dest1, path1, 5000.0, 300000L);
        routeRepository.save(r1);

        // Route 2 is far away (e.g. at 101.7500, 3.2000)
        Point origin2 = GeometryUtils.createPoint(101.7500, 3.2000);
        Point dest2 = GeometryUtils.createPoint(101.7800, 3.2200);
        LineString path2 = GeometryUtils.createLineString(List.of(
                List.of(101.7500, 3.2000),
                List.of(101.7800, 3.2200)
        ));
        Route r2 = new Route("RT-FAR-02", "OPTIMAL", origin2, dest2, path2, 3000.0, 200000L);
        routeRepository.save(r2);

        // Bounding box around (101.6200, 3.1150)
        Coordinate[] ring = new Coordinate[]{
                new Coordinate(101.6150, 3.1100),
                new Coordinate(101.6250, 3.1100),
                new Coordinate(101.6250, 3.1200),
                new Coordinate(101.6150, 3.1200),
                new Coordinate(101.6150, 3.1100)
        };
        Polygon box = GF.createPolygon(ring);

        List<Route> intersecting = routeRepository.findRoutesIntersecting(box);

        assertThat(intersecting).isNotEmpty();
        List<String> ids = intersecting.stream().map(Route::getId).toList();
        assertThat(ids).contains("RT-INT-01");
        assertThat(ids).doesNotContain("RT-FAR-02");
    }
}
