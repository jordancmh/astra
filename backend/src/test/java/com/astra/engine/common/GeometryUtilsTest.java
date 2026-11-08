package com.astra.engine.common;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void createLineString_shouldCreateJtsLineString_whenCoordinatesAreValid() {
        List<List<Double>> coords = List.of(
                List.of(101.6839, 3.1390),
                List.of(101.5540, 3.0755)
        );

        LineString line = GeometryUtils.createLineString(coords);

        assertThat(line).isNotNull();
        assertThat(line.getSRID()).isEqualTo(4326);
        assertThat(line.getNumPoints()).isEqualTo(2);
        assertThat(line.getCoordinateN(0).getX()).isEqualTo(101.6839);
        assertThat(line.getCoordinateN(0).getY()).isEqualTo(3.1390);
        assertThat(line.getCoordinateN(1).getX()).isEqualTo(101.5540);
        assertThat(line.getCoordinateN(1).getY()).isEqualTo(3.0755);
    }

    @Test
    void createLineString_shouldThrowIllegalArgumentException_whenCoordinatesHaveLessThanTwoPoints() {
        assertThatThrownBy(() -> GeometryUtils.createLineString(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 coordinate points");

        assertThatThrownBy(() -> GeometryUtils.createLineString(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 coordinate points");

        assertThatThrownBy(() -> GeometryUtils.createLineString(List.of(List.of(101.6839, 3.1390))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 2 coordinate points");
    }

    @Test
    void createLineString_shouldThrowIllegalArgumentException_whenCoordinateIsOutOfBounds() {
        List<List<Double>> outOfBoundsCoords = List.of(
                List.of(101.6839, 3.1390),
                List.of(105.0000, 5.0000) // Outside Klang Valley
        );

        assertThatThrownBy(() -> GeometryUtils.createLineString(outOfBoundsCoords))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside Klang Valley bounding box");
    }

    @Test
    void isWithinKlangValley_shouldValidateBoundsAccurately() {
        assertThat(GeometryUtils.isWithinKlangValley(101.6371, 3.1140)).isTrue();
        assertThat(GeometryUtils.isWithinKlangValley(101.40, 2.90)).isTrue();
        assertThat(GeometryUtils.isWithinKlangValley(101.90, 3.30)).isTrue();

        assertThat(GeometryUtils.isWithinKlangValley(101.39, 3.00)).isFalse();
        assertThat(GeometryUtils.isWithinKlangValley(101.91, 3.00)).isFalse();
        assertThat(GeometryUtils.isWithinKlangValley(101.60, 2.89)).isFalse();
        assertThat(GeometryUtils.isWithinKlangValley(101.60, 3.31)).isFalse();
    }
}
