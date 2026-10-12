package com.astra.engine.common;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.assertj.core.api.Assertions.assertThat;

class GeometryUtilsTest {

    @Test
    void createPoint_shouldEnforceSrid4326AndCoordinateOrder() {
        double longitude = 101.6371;
        double latitude = 3.1140;

        Point point = GeometryUtils.createPoint(longitude, latitude);

        assertThat(point).isNotNull();
        assertThat(point.getSRID()).isEqualTo(4326);
        assertThat(point.getX()).isEqualTo(longitude);
        assertThat(point.getY()).isEqualTo(latitude);
    }
}
