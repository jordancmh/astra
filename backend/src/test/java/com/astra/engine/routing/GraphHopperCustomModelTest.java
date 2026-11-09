package com.astra.engine.routing;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GraphHopperCustomModelTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void ofPolygon_serializesToGraphHopperCustomModelFormat() throws Exception {
        List<List<Double>> polygonRing = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        );

        GraphHopperCustomModel model = GraphHopperCustomModel.ofPolygon("anomaly_d001", polygonRing, 0.0);

        String json = objectMapper.writeValueAsString(model);

        assertThat(json).contains("\"speed\":[{\"if\":\"in_anomaly_d001\",\"multiply_by\":\"0\"}]");
        assertThat(json).contains("\"areas\":{\"type\":\"FeatureCollection\"");
        assertThat(json).contains("\"id\":\"anomaly_d001\"");
        assertThat(json).contains("\"type\":\"Polygon\"");
        assertThat(json).contains("101.615,3.112");
    }

    @Test
    void ofPolygon_withCongestionPenalty_serializesMultiplier() throws Exception {
        List<List<Double>> polygonRing = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124),
                List.of(101.615, 3.112)
        );

        GraphHopperCustomModel model = GraphHopperCustomModel.ofPolygon("anomaly_d002", polygonRing, 0.2);

        String json = objectMapper.writeValueAsString(model);

        assertThat(json).contains("\"speed\":[{\"if\":\"in_anomaly_d002\",\"multiply_by\":\"0.2\"}]");
    }

    @Test
    void ofPolygon_withUnclosedRing_automaticallyClosesRing() {
        List<List<Double>> unclosedRing = List.of(
                List.of(101.615, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(101.615, 3.124)
        );

        GraphHopperCustomModel model = GraphHopperCustomModel.ofPolygon("d003", unclosedRing, 0.0);

        List<List<Double>> ring = (List<List<Double>>) ((List<?>) model.areas().features().get(0).geometry().get("coordinates")).get(0);
        assertThat(ring).hasSize(5);
        assertThat(ring.get(0)).isEqualTo(ring.get(4));
    }

    @Test
    void ofPolygon_withOutOfBoundsCoordinates_throwsIllegalArgumentException() {
        List<List<Double>> outOfBoundsRing = List.of(
                List.of(100.0, 3.112),
                List.of(101.627, 3.112),
                List.of(101.627, 3.124),
                List.of(100.0, 3.112)
        );

        assertThatThrownBy(() -> GraphHopperCustomModel.ofPolygon("invalid_area", outOfBoundsRing, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside Klang Valley");
    }

    @Test
    void ofCircle_generatesValidClosedPolygonWithinKlangValley() throws Exception {
        double centerLng = 101.6210;
        double centerLat = 3.1180;
        double radiusMeters = 600.0;

        GraphHopperCustomModel model = GraphHopperCustomModel.ofCircle("anomaly_circle", centerLng, centerLat, radiusMeters, 0.0);

        String json = objectMapper.writeValueAsString(model);
        assertThat(json).contains("\"if\":\"in_anomaly_circle\"");
        assertThat(json).contains("\"multiply_by\":\"0\"");

        List<List<Double>> ring = (List<List<Double>>) ((List<?>) model.areas().features().get(0).geometry().get("coordinates")).get(0);
        assertThat(ring).isNotEmpty();
        // First and last points match
        assertThat(ring.get(0)).isEqualTo(ring.get(ring.size() - 1));

        for (List<Double> pt : ring) {
            assertThat(GeometryUtils.isWithinKlangValley(pt.get(0), pt.get(1))).isTrue();
        }
    }
}
