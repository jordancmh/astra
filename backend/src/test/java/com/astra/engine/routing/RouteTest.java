package com.astra.engine.routing;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTest {

    @Test
    void route_shouldInstantiateAndRetainGeometryProperties() {
        Point origin = GeometryUtils.createPoint(101.6839, 3.1390);
        Point dest = GeometryUtils.createPoint(101.5540, 3.0755);
        LineString path = GeometryUtils.createLineString(List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        ));

        Route route = new Route("RT-001", "OPTIMAL", origin, dest, path, 15400.0, 1200000L);

        assertThat(route.getId()).isEqualTo("RT-001");
        assertThat(route.getStatus()).isEqualTo("OPTIMAL");
        assertThat(route.getOriginPoint()).isEqualTo(origin);
        assertThat(route.getDestinationPoint()).isEqualTo(dest);
        assertThat(route.getPathGeometry()).isEqualTo(path);
        assertThat(route.getDistanceMeters()).isEqualTo(15400.0);
        assertThat(route.getEstimatedTimeMs()).isEqualTo(1200000L);
        assertThat(route.getUpdatedAt()).isNotNull();

        route.onUpdate();
        assertThat(route.getUpdatedAt()).isBeforeOrEqualTo(Instant.now());
    }
}
