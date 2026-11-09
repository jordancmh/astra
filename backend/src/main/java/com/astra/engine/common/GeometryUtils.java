package com.astra.engine.common;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

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

    public static boolean isWithinKlangValley(double longitude, double latitude) {
        return longitude >= MIN_LNG && longitude <= MAX_LNG
                && latitude >= MIN_LAT && latitude <= MAX_LAT;
    }
}
