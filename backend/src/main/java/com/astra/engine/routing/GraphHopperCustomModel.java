package com.astra.engine.routing;

import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.dto.GraphHopperAreaCollection;
import com.astra.engine.routing.dto.GraphHopperAreaFeature;
import com.astra.engine.routing.dto.GraphHopperSpeedStatement;

import java.util.ArrayList;
import java.util.List;

public record GraphHopperCustomModel(
        List<GraphHopperSpeedStatement> speed,
        GraphHopperAreaCollection areas
) {
    public static GraphHopperCustomModel empty() {
        return new GraphHopperCustomModel(List.of(), GraphHopperAreaCollection.of(List.of()));
    }

    public static GraphHopperCustomModel ofPolygon(
            String areaId,
            List<List<Double>> exteriorRing,
            double multiplyBy
    ) {
        if (areaId == null || areaId.isBlank()) {
            throw new IllegalArgumentException("areaId cannot be empty");
        }
        if (exteriorRing == null || exteriorRing.size() < 3) {
            throw new IllegalArgumentException("exteriorRing must contain at least 3 points");
        }

        List<List<Double>> validatedRing = new ArrayList<>();
        for (List<Double> pt : exteriorRing) {
            if (pt == null || pt.size() < 2) {
                throw new IllegalArgumentException("Coordinate must contain [longitude, latitude]");
            }
            double lng = pt.get(0);
            double lat = pt.get(1);
            if (!GeometryUtils.isWithinKlangValley(lng, lat)) {
                throw new IllegalArgumentException(String.format(
                        "Coordinate [%f, %f] is outside Klang Valley bounding box (Lat: %.2f..%.2f, Lng: %.2f..%.2f)",
                        lng, lat, GeometryUtils.MIN_LAT, GeometryUtils.MAX_LAT, GeometryUtils.MIN_LNG, GeometryUtils.MAX_LNG));
            }
            validatedRing.add(List.of(lng, lat));
        }

        // Ensure linear ring is closed (first point equals last point)
        List<Double> first = validatedRing.get(0);
        List<Double> last = validatedRing.get(validatedRing.size() - 1);
        if (!first.get(0).equals(last.get(0)) || !first.get(1).equals(last.get(1))) {
            validatedRing.add(List.of(first.get(0), first.get(1)));
        }

        String sanitizedId = sanitizeAreaId(areaId);
        String multiplierStr = formatMultiplyBy(multiplyBy);

        GraphHopperSpeedStatement speedStmt = new GraphHopperSpeedStatement(
                "in_" + sanitizedId,
                multiplierStr
        );

        GraphHopperAreaFeature feature = GraphHopperAreaFeature.ofPolygon(
                sanitizedId,
                List.of(validatedRing)
        );

        return new GraphHopperCustomModel(
                List.of(speedStmt),
                GraphHopperAreaCollection.of(List.of(feature))
        );
    }

    public static GraphHopperCustomModel ofCircle(
            String areaId,
            double centerLng,
            double centerLat,
            double radiusMeters,
            double multiplyBy
    ) {
        if (!GeometryUtils.isWithinKlangValley(centerLng, centerLat)) {
            throw new IllegalArgumentException(String.format(
                    "Center [%f, %f] is outside Klang Valley bounding box", centerLng, centerLat));
        }
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        // Approximate meters to degrees for Klang Valley (~3.1° N)
        double latDegreeMeters = 110574.0;
        double lngDegreeMeters = 111157.0;

        double dLat = radiusMeters / latDegreeMeters;
        double dLng = radiusMeters / lngDegreeMeters;

        // Approximate circular polygon with 16 vertices
        int points = 16;
        List<List<Double>> ring = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double lng = centerLng + dLng * Math.cos(angle);
            double lat = centerLat + dLat * Math.sin(angle);

            // Clamp strictly within Klang Valley bounds
            lng = Math.max(GeometryUtils.MIN_LNG, Math.min(GeometryUtils.MAX_LNG, lng));
            lat = Math.max(GeometryUtils.MIN_LAT, Math.min(GeometryUtils.MAX_LAT, lat));

            ring.add(List.of(lng, lat));
        }
        // Close the ring
        ring.add(new ArrayList<>(ring.get(0)));

        return ofPolygon(areaId, ring, multiplyBy);
    }

    private static String sanitizeAreaId(String id) {
        return id.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
    }

    private static String formatMultiplyBy(double multiplyBy) {
        if (multiplyBy == 0.0) {
            return "0";
        }
        if (multiplyBy == (long) multiplyBy) {
            return String.valueOf((long) multiplyBy);
        }
        return String.valueOf(multiplyBy);
    }
}
