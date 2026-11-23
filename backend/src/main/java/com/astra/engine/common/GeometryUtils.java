package com.astra.engine.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;

import java.util.ArrayList;
import java.util.List;

public final class GeometryUtils {

    public static final int SRID = 4326;
    public static final double MIN_LAT = 2.90;
    public static final double MAX_LAT = 3.30;
    public static final double MIN_LNG = 101.40;
    public static final double MAX_LNG = 101.90;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    private GeometryUtils() {
    }

    public static Point createPoint(double longitude, double latitude) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
    }

    public static LineString createLineString(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("LineString requires at least 2 coordinate points");
        }

        Coordinate[] jtsCoordinates = new Coordinate[coordinates.size()];
        for (int i = 0; i < coordinates.size(); i++) {
            List<Double> coord = coordinates.get(i);
            if (coord == null || coord.size() < 2) {
                throw new IllegalArgumentException("Each coordinate must contain [longitude, latitude]");
            }
            double lng = coord.get(0);
            double lat = coord.get(1);
            if (!isWithinKlangValley(lng, lat)) {
                throw new IllegalArgumentException(String.format(
                        "Coordinate [%f, %f] is outside Klang Valley bounding box (Lat: %.2f..%.2f, Lng: %.2f..%.2f)",
                        lng, lat, MIN_LAT, MAX_LAT, MIN_LNG, MAX_LNG));
            }
            jtsCoordinates[i] = new Coordinate(lng, lat);
        }

        return GEOMETRY_FACTORY.createLineString(jtsCoordinates);
    }

    public static Polygon createGeodesicCirclePolygon(double centerLng, double centerLat, double radiusMeters) {
        if (!isWithinKlangValley(centerLng, centerLat)) {
            throw new IllegalArgumentException(String.format(
                    "Center [%f, %f] is outside Klang Valley bounding box (Lat: %.2f..%.2f, Lng: %.2f..%.2f)",
                    centerLng, centerLat, MIN_LAT, MAX_LAT, MIN_LNG, MAX_LNG));
        }
        if (radiusMeters <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        double latDegreeMeters = 110574.0;
        double lngDegreeMeters = 111157.0;

        double dLat = radiusMeters / latDegreeMeters;
        double dLng = radiusMeters / lngDegreeMeters;

        int points = 16;
        Coordinate[] coords = new Coordinate[points + 1];
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double lng = centerLng + dLng * Math.cos(angle);
            double lat = centerLat + dLat * Math.sin(angle);

            lng = Math.max(MIN_LNG, Math.min(MAX_LNG, lng));
            lat = Math.max(MIN_LAT, Math.min(MAX_LAT, lat));

            coords[i] = new Coordinate(lng, lat);
        }
        coords[points] = new Coordinate(coords[0].x, coords[0].y);

        return GEOMETRY_FACTORY.createPolygon(coords);
    }

    public static GeoJsonPolygon toGeoJsonPolygon(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        Coordinate[] exterior = polygon.getExteriorRing().getCoordinates();
        List<List<Double>> ring = new ArrayList<>();
        for (Coordinate c : exterior) {
            ring.add(List.of(c.x, c.y));
        }
        return GeoJsonPolygon.ofSingleRing(ring);
    }

    public static GeoJsonLineString toGeoJsonLineString(LineString lineString) {
        if (lineString == null) {
            return null;
        }
        Coordinate[] coordinates = lineString.getCoordinates();
        List<List<Double>> coordsList = new ArrayList<>(coordinates.length);
        for (Coordinate c : coordinates) {
            coordsList.add(List.of(c.x, c.y));
        }
        return new GeoJsonLineString(coordsList);
    }

    public static double calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(lat2Rad);
        double x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
    }

    public static boolean isWithinKlangValley(double longitude, double latitude) {
        return longitude >= MIN_LNG && longitude <= MAX_LNG
                && latitude >= MIN_LAT && latitude <= MAX_LAT;
    }
}
