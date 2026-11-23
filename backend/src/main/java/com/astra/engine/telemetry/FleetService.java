package com.astra.engine.telemetry;

import com.astra.engine.anomaly.NetworkAnomaly;
import com.astra.engine.anomaly.NetworkAnomalyRepository;
import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.Route;
import com.astra.engine.routing.RouteRepository;
import com.astra.engine.telemetry.dto.FleetRouteDto;
import com.astra.engine.telemetry.dto.FleetStateResponse;
import com.astra.engine.telemetry.dto.FleetSummaryDto;
import com.astra.engine.telemetry.dto.FleetVehicleDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FleetService {

    private final VehicleRepository vehicleRepository;
    private final RouteRepository routeRepository;
    private final NetworkAnomalyRepository anomalyRepository;

    public FleetService(VehicleRepository vehicleRepository,
                        RouteRepository routeRepository,
                        NetworkAnomalyRepository anomalyRepository) {
        this.vehicleRepository = vehicleRepository;
        this.routeRepository = routeRepository;
        this.anomalyRepository = anomalyRepository;
    }

    @Transactional(readOnly = true)
    public FleetStateResponse getFleetState() {
        List<Vehicle> vehicles = vehicleRepository.findAllByOrderByCargoPriorityDesc();
        List<Route> routes = routeRepository.findAll();
        List<NetworkAnomaly> activeAnomalies = anomalyRepository.findByIsActiveTrueOrderByInjectedAtDesc();

        Map<String, Route> routeMap = routes.stream()
                .collect(Collectors.toMap(Route::getId, r -> r, (r1, r2) -> r1));

        Map<String, String> routeToVehicleMap = vehicles.stream()
                .filter(v -> v.getCurrentRouteId() != null)
                .collect(Collectors.toMap(Vehicle::getCurrentRouteId, Vehicle::getId, (v1, v2) -> v1));

        List<FleetVehicleDto> vehicleDtos = new ArrayList<>();
        int nominalCount = 0;
        int reroutingCount = 0;
        int disruptedCount = 0;

        for (Vehicle vehicle : vehicles) {
            String routeId = vehicle.getCurrentRouteId();
            Route route = routeId != null ? routeMap.get(routeId) : null;
            String intersectedAnomalyId = null;
            String status = vehicle.getStatus() != null ? vehicle.getStatus().toUpperCase() : "NOMINAL";

            if (route != null && route.getPathGeometry() != null) {
                for (NetworkAnomaly anomaly : activeAnomalies) {
                    if (anomaly.getBoundingBox() != null && route.getPathGeometry().intersects(anomaly.getBoundingBox())) {
                        intersectedAnomalyId = anomaly.getId();
                        status = "DISRUPTED";
                        break;
                    }
                }
            }

            if ("REROUTING".equals(status) || (route != null && "REROUTING".equalsIgnoreCase(route.getStatus()))) {
                status = "REROUTING";
            }

            switch (status) {
                case "DISRUPTED" -> disruptedCount++;
                case "REROUTING" -> reroutingCount++;
                default -> nominalCount++;
            }

            double lat = vehicle.getCurrentLocation() != null ? vehicle.getCurrentLocation().getY() : 0.0;
            double lng = vehicle.getCurrentLocation() != null ? vehicle.getCurrentLocation().getX() : 0.0;

            vehicleDtos.add(new FleetVehicleDto(
                    vehicle.getId(),
                    routeId,
                    lat,
                    lng,
                    vehicle.getBearing() != null ? vehicle.getBearing() : 0.0,
                    vehicle.getSpeedKmh() != null ? vehicle.getSpeedKmh() : 0.0,
                    vehicle.getCargoPriority() != null ? vehicle.getCargoPriority() : 1,
                    status,
                    intersectedAnomalyId,
                    vehicle.getUpdatedAt() != null ? vehicle.getUpdatedAt() : Instant.now()
            ));
        }

        List<FleetRouteDto> routeDtos = routes.stream()
                .map(r -> new FleetRouteDto(
                        r.getId(),
                        routeToVehicleMap.get(r.getId()),
                        r.getStatus(),
                        GeometryUtils.toGeoJsonLineString(r.getPathGeometry()),
                        r.getDistanceMeters(),
                        r.getEstimatedTimeMs()
                ))
                .toList();

        FleetSummaryDto summary = new FleetSummaryDto(
                vehicleDtos.size(),
                nominalCount,
                reroutingCount,
                disruptedCount
        );

        return new FleetStateResponse(summary, vehicleDtos, routeDtos, Instant.now());
    }

    @Transactional
    public void updateVehicleRouteAndStatus(String vehicleId, String routeId, String status) {
        if (vehicleId == null || vehicleId.isBlank()) {
            return;
        }
        vehicleRepository.findById(vehicleId.trim()).ifPresent(vehicle -> {
            if (routeId != null && !routeId.isBlank()) {
                vehicle.setCurrentRouteId(routeId.trim());
            }
            if (status != null && !status.isBlank()) {
                vehicle.setStatus(status.trim().toUpperCase());
            }
            vehicle.setUpdatedAt(Instant.now());
            vehicleRepository.save(vehicle);
        });
    }

    @Transactional
    public void normalizeVehicles() {
        List<Vehicle> vehicles = vehicleRepository.findAll();
        List<NetworkAnomaly> activeAnomalies = anomalyRepository.findByIsActiveTrueOrderByInjectedAtDesc();
        List<Route> routes = routeRepository.findAll();
        Map<String, Route> routeMap = routes.stream()
                .collect(Collectors.toMap(Route::getId, r -> r, (r1, r2) -> r1));

        for (Vehicle v : vehicles) {
            String currStatus = v.getStatus() != null ? v.getStatus().toUpperCase() : "NOMINAL";
            if ("DISRUPTED".equals(currStatus) || "REROUTING".equals(currStatus)) {
                Route route = v.getCurrentRouteId() != null ? routeMap.get(v.getCurrentRouteId()) : null;
                boolean stillIntersected = false;
                if (route != null && route.getPathGeometry() != null) {
                    for (NetworkAnomaly a : activeAnomalies) {
                        if (a.getBoundingBox() != null && route.getPathGeometry().intersects(a.getBoundingBox())) {
                            stillIntersected = true;
                            break;
                        }
                    }
                }
                if (!stillIntersected) {
                    v.setStatus("NOMINAL");
                    v.setUpdatedAt(Instant.now());
                    vehicleRepository.save(v);
                }
            }
        }
    }
}
