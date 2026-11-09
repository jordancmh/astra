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

    public RoutingService(GraphHopperClient graphHopperClient, RouteRepository routeRepository) {
        this.graphHopperClient = graphHopperClient;
        this.routeRepository = routeRepository;
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

    @Transactional
    public RouteUpdateResult updateRoute(String vehicleId, String routeId, List<List<Double>> coordinates, String rationale) {
        LineString pathGeometry = GeometryUtils.createLineString(coordinates);
        Point origin = (Point) pathGeometry.getStartPoint();
        Point destination = (Point) pathGeometry.getEndPoint();

        Route route = routeRepository.findById(routeId).orElseGet(Route::new);
        route.setId(routeId);
        route.setStatus("OPTIMAL");
        route.setOriginPoint(origin);
        route.setDestinationPoint(destination);
        route.setPathGeometry(pathGeometry);
        route.setUpdatedAt(Instant.now());

        Route saved = routeRepository.save(route);

        return new RouteUpdateResult(
                "COMMITTED",
                vehicleId,
                saved.getId(),
                coordinates.size(),
                new GeoJsonLineString(coordinates),
                saved.getUpdatedAt()
        );
    }
}
