package com.astra.engine.routing;

import java.util.List;

public record GraphHopperRouteResponse(
        List<GraphHopperPath> paths
) {
    public record GraphHopperPath(
            Double distance,
            Long time,
            GraphHopperPoints points
    ) {}

    public record GraphHopperPoints(
            String type,
            List<List<Double>> coordinates
    ) {}
}
