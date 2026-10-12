package com.astra.engine.routing;

import com.astra.engine.common.GeoJsonLineString;
import org.springframework.stereotype.Service;

@Service
public class RoutingService {

    private final GraphHopperClient graphHopperClient;

    public RoutingService(GraphHopperClient graphHopperClient) {
        this.graphHopperClient = graphHopperClient;
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
}
