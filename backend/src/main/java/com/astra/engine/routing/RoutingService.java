package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import com.astra.engine.common.GeometryUtils;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class RoutingService {

    private final GraphHopperClient graphHopperClient;
    private final RouteRepository routeRepository;
    private final NegotiationEventRepository negotiationEventRepository;

    public RoutingService(GraphHopperClient graphHopperClient,
                          RouteRepository routeRepository,
                          NegotiationEventRepository negotiationEventRepository) {
        this.graphHopperClient = graphHopperClient;
        this.routeRepository = routeRepository;
        this.negotiationEventRepository = negotiationEventRepository;
    }

    public RouteCalculateResponse calculateRoute(RouteCalculateRequest request) {
        GraphHopperRouteResponse.GraphHopperPath path = graphHopperClient.route(
                request.originLng(),
                request.originLat(),
                request.destLng(),
                request.destLat(),
                request.avoidAnomalyIds()
        );

        GeoJsonLineString geometry = new GeoJsonLineString(path.points().coordinates());

        return new RouteCalculateResponse(
                request.routeId(),
                "OPTIMAL",
                path.distance(),
                path.time(),
                geometry
        );
    }

    public RouteCalculateResponse recalculateDetour(
            String routeId,
            String vehicleId,
            double originLng, double originLat,
            double destLng, double destLat,
            List<List<Double>> avoidPolygon,
            double penaltyFactor,
            String anomalyId
    ) {
        if (!GeometryUtils.isWithinKlangValley(originLng, originLat)) {
            throw new IllegalArgumentException(String.format("Origin [%f, %f] is outside Klang Valley", originLng, originLat));
        }
        if (!GeometryUtils.isWithinKlangValley(destLng, destLat)) {
            throw new IllegalArgumentException(String.format("Destination [%f, %f] is outside Klang Valley", destLng, destLat));
        }

        GraphHopperCustomModel customModel;
        if (avoidPolygon != null && !avoidPolygon.isEmpty()) {
            customModel = GraphHopperCustomModel.ofPolygon(
                    anomalyId != null && !anomalyId.isBlank() ? anomalyId : "obstacle",
                    avoidPolygon,
                    penaltyFactor
            );
        } else {
            customModel = GraphHopperCustomModel.empty();
        }

        GraphHopperRouteResponse.GraphHopperPath path = graphHopperClient.routeWithCustomModel(
                originLng, originLat, destLng, destLat, customModel
        );

        for (List<Double> coord : path.points().coordinates()) {
            if (!GeometryUtils.isWithinKlangValley(coord.get(0), coord.get(1))) {
                throw new IllegalArgumentException(String.format(
                        "GraphHopper produced out-of-bounds coordinate [%f, %f]", coord.get(0), coord.get(1)));
            }
        }

        GeoJsonLineString geometry = new GeoJsonLineString(path.points().coordinates());

        return new RouteCalculateResponse(
                routeId,
                "OPTIMAL",
                path.distance(),
                path.time(),
                geometry
        );
    }

    @Transactional
    public RouteUpdateResult updateRoute(
            String vehicleId,
            String routeId,
            List<List<Double>> coordinates,
            String rationale
    ) {
        return updateRoute(vehicleId, routeId, coordinates, null, null, rationale, null);
    }

    @Transactional
    public RouteUpdateResult updateRoute(
            String vehicleId,
            String routeId,
            List<List<Double>> coordinates,
            Double distanceMeters,
            Long estimatedTimeMs,
            String rationale,
            String anomalyId
    ) {
        LineString pathGeometry = GeometryUtils.createLineString(coordinates);
        Point origin = (Point) pathGeometry.getStartPoint();
        Point destination = (Point) pathGeometry.getEndPoint();

        Route route = routeRepository.findById(routeId).orElseGet(Route::new);
        route.setId(routeId);
        route.setStatus("OPTIMAL");
        route.setOriginPoint(origin);
        route.setDestinationPoint(destination);
        route.setPathGeometry(pathGeometry);
        if (distanceMeters != null) {
            route.setDistanceMeters(distanceMeters);
        }
        if (estimatedTimeMs != null) {
            route.setEstimatedTimeMs(estimatedTimeMs);
        }
        route.setUpdatedAt(Instant.now());

        Route saved = routeRepository.save(route);

        if (rationale != null && !rationale.isBlank()) {
            NegotiationEvent event = new NegotiationEvent(
                    anomalyId,
                    vehicleId,
                    routeId,
                    rationale.trim(),
                    Instant.now()
            );
            negotiationEventRepository.save(event);
        }

        return new RouteUpdateResult(
                "COMMITTED",
                vehicleId,
                saved.getId(),
                coordinates.size(),
                new GeoJsonLineString(coordinates),
                saved.getDistanceMeters(),
                saved.getEstimatedTimeMs(),
                saved.getUpdatedAt()
        );
    }

    @Transactional
    public RouteUpdateResult recalculateAndCommitDetour(
            String vehicleId,
            String routeId,
            double originLng, double originLat,
            double destLng, double destLat,
            List<List<Double>> avoidPolygon,
            double penaltyFactor,
            String rationale,
            String anomalyId
    ) {
        RouteCalculateResponse calculated = recalculateDetour(
                routeId, vehicleId, originLng, originLat, destLng, destLat, avoidPolygon, penaltyFactor, anomalyId
        );

        return updateRoute(
                vehicleId,
                routeId,
                calculated.geometry().coordinates(),
                calculated.distanceMeters(),
                calculated.estimatedTimeMs(),
                rationale,
                anomalyId
        );
    }
}
