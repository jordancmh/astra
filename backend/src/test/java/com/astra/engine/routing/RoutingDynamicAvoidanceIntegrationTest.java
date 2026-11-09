package com.astra.engine.routing;

import com.astra.engine.common.GeometryUtils;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoutingDynamicAvoidanceIntegrationTest {

    @Autowired
    private RoutingService routingService;

    @Autowired
    private RouteRepository routeRepository;

    @Autowired
    private NegotiationEventRepository negotiationEventRepository;

    @Test
    @Transactional
    void recalculateAndCommitDetour_liveGraphHopperAndPostGis_commitsDetourAndNegotiationEvent() {
        double originLng = 101.4496; // Port Klang
        double originLat = 3.0430;
        double destLng = 101.7116;   // KL City Centre
        double destLat = 3.1579;

        // 1. Calculate unobstructed base route
        RouteCalculateRequest baseRequest = new RouteCalculateRequest(
                "RT-BASE-001", "V-BASE", originLat, originLng, destLat, destLng, List.of()
        );
        RouteCalculateResponse baseRoute = routingService.calculateRoute(baseRequest);
        assertThat(baseRoute.distanceMeters()).isGreaterThan(20000.0);

        // 2. Define obstacle polygon across primary highway corridor (Shah Alam / Subang)
        List<List<Double>> obstaclePolygon = List.of(
                List.of(101.500, 3.050),
                List.of(101.550, 3.050),
                List.of(101.550, 3.100),
                List.of(101.500, 3.100),
                List.of(101.500, 3.050)
        );

        String vehicleId = "V-INTEG-004";
        String routeId = "RT-INTEG-004";
        String anomalyId = "D-SUBANG-001";
        String rationale = "V-004 rerouted around road closure D-SUBANG-001 via alternative corridor.";

        // 3. Recalculate and commit detour with dynamic polygon avoidance (multiply_by: 0)
        RouteUpdateResult result = routingService.recalculateAndCommitDetour(
                vehicleId,
                routeId,
                originLng, originLat,
                destLng, destLat,
                obstaclePolygon,
                0.0, // Impassable closure
                rationale,
                anomalyId
        );

        // 4. Verify result DTO
        assertThat(result.status()).isEqualTo("COMMITTED");
        assertThat(result.vehicleId()).isEqualTo(vehicleId);
        assertThat(result.routeId()).isEqualTo(routeId);
        assertThat(result.distanceMeters()).isNotNull();
        // GraphHopper took a detour around the 5x5 km box, so distance should be greater
        assertThat(result.distanceMeters()).isGreaterThan(baseRoute.distanceMeters());
        assertThat(result.estimatedTimeMs()).isGreaterThan(baseRoute.estimatedTimeMs());

        // 5. Verify Route persisted in PostGIS
        Optional<Route> savedRouteOpt = routeRepository.findById(routeId);
        assertThat(savedRouteOpt).isPresent();
        Route savedRoute = savedRouteOpt.get();
        assertThat(savedRoute.getStatus()).isEqualTo("OPTIMAL");
        assertThat(savedRoute.getDistanceMeters()).isEqualTo(result.distanceMeters());
        assertThat(savedRoute.getEstimatedTimeMs()).isEqualTo(result.estimatedTimeMs());
        assertThat(savedRoute.getPathGeometry()).isNotNull();
        assertThat(savedRoute.getPathGeometry().getSRID()).isEqualTo(4326);

        // Verify every coordinate is within Klang Valley
        for (Coordinate c : savedRoute.getPathGeometry().getCoordinates()) {
            assertThat(GeometryUtils.isWithinKlangValley(c.getX(), c.getY())).isTrue();
        }

        // 6. Verify NegotiationEvent persisted in PostGIS
        List<NegotiationEvent> events = negotiationEventRepository.findByVehicleIdOrderByCreatedAtDesc(vehicleId);
        assertThat(events).isNotEmpty();
        NegotiationEvent event = events.get(0);
        assertThat(event.getAnomalyId()).isEqualTo(anomalyId);
        assertThat(event.getAffectedRoutes()).isEqualTo(routeId);
        assertThat(event.getEventMessage()).isEqualTo(rationale);
        assertThat(event.getCreatedAt()).isNotNull();
    }
}
