package com.astra.engine.anomaly;

import com.astra.engine.anomaly.dto.AnomalyClearResponse;
import com.astra.engine.anomaly.dto.AnomalyDto;
import com.astra.engine.anomaly.dto.AnomalyInjectRequest;
import com.astra.engine.anomaly.dto.AnomalyInjectResponse;
import com.astra.engine.common.GeometryUtils;
import com.astra.engine.routing.RoutingService;
import com.astra.engine.telemetry.TelemetryService;
import com.astra.engine.telemetry.VehicleStatusDto;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class AnomalyService {

    private final NetworkAnomalyRepository repository;
    private final RoutingService routingService;
    private final TelemetryService telemetryService;
    private final AtomicInteger idCounter = new AtomicInteger(1);
    private volatile boolean idInitialized = false;

    public AnomalyService(NetworkAnomalyRepository repository,
                          RoutingService routingService,
                          TelemetryService telemetryService) {
        this.repository = repository;
        this.routingService = routingService;
        this.telemetryService = telemetryService;
    }

    @Transactional
    public AnomalyInjectResponse injectAnomaly(AnomalyInjectRequest request) {
        double lng = request.longitude();
        double lat = request.latitude();

        if (!GeometryUtils.isWithinKlangValley(lng, lat)) {
            throw new IllegalArgumentException(String.format(
                    "Coordinates [%f, %f] are outside Klang Valley bounds (Lat: %.2f..%.2f, Lng: %.2f..%.2f)",
                    lng, lat, GeometryUtils.MIN_LAT, GeometryUtils.MAX_LAT, GeometryUtils.MIN_LNG, GeometryUtils.MAX_LNG));
        }

        if (request.radiusMeters() <= 0) {
            throw new IllegalArgumentException("radiusMeters must be positive");
        }

        String anomalyId = generateNextAnomalyId();
        Point center = GeometryUtils.createPoint(lng, lat);
        Polygon boundingBox = GeometryUtils.createGeodesicCirclePolygon(lng, lat, request.radiusMeters());

        NetworkAnomaly anomaly = new NetworkAnomaly(
                anomalyId,
                request.anomalyType().trim().toUpperCase(),
                request.severity().trim().toUpperCase(),
                center,
                boundingBox,
                request.radiusMeters(),
                true,
                Instant.now()
        );

        NetworkAnomaly saved = repository.save(anomaly);

        List<String> affectedRouteIds = routingService.findIntersectingRouteIds(boundingBox);

        List<String> affectedVehicleIds = List.of();
        if (!affectedRouteIds.isEmpty()) {
            List<VehicleStatusDto> fleet = telemetryService.getFleetState(null);
            affectedVehicleIds = fleet.stream()
                    .filter(v -> v.routeId() != null && affectedRouteIds.contains(v.routeId()))
                    .map(VehicleStatusDto::vehicleId)
                    .toList();
        }

        return new AnomalyInjectResponse(
                saved.getId(),
                saved.getAnomalyType(),
                saved.getSeverity(),
                affectedRouteIds,
                affectedVehicleIds,
                saved.getInjectedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<AnomalyDto> getActiveAnomalies() {
        return repository.findByIsActiveTrueOrderByInjectedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AnomalyDto> getAnomalyById(String id) {
        return repository.findById(id).map(this::toDto);
    }

    @Transactional
    public boolean deleteAnomaly(String id) {
        Optional<NetworkAnomaly> opt = repository.findById(id);
        if (opt.isPresent()) {
            NetworkAnomaly anomaly = opt.get();
            anomaly.setActive(false);
            repository.save(anomaly);
            return true;
        }
        return false;
    }

    @Transactional
    public AnomalyClearResponse clearAllAnomalies() {
        List<NetworkAnomaly> active = repository.findByIsActiveTrueOrderByInjectedAtDesc();
        for (NetworkAnomaly a : active) {
            a.setActive(false);
        }
        repository.saveAll(active);
        return new AnomalyClearResponse(active.size(), Instant.now());
    }

    private AnomalyDto toDto(NetworkAnomaly a) {
        List<Double> center = a.getCenterPoint() != null
                ? List.of(a.getCenterPoint().getX(), a.getCenterPoint().getY())
                : List.of();

        return new AnomalyDto(
                a.getId(),
                a.getId(),
                a.getAnomalyType(),
                a.getSeverity(),
                a.getRadiusMeters(),
                center,
                GeometryUtils.toGeoJsonPolygon(a.getBoundingBox()),
                a.getInjectedAt(),
                a.isActive()
        );
    }

    private synchronized String generateNextAnomalyId() {
        if (!idInitialized) {
            try {
                int max = 0;
                for (NetworkAnomaly a : repository.findAll()) {
                    if (a.getId() != null && a.getId().startsWith("D-")) {
                        try {
                            int num = Integer.parseInt(a.getId().substring(2));
                            if (num > max) {
                                max = num;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                idCounter.set(max + 1);
            } catch (Exception ignored) {
            }
            idInitialized = true;
        }
        return "D-" + String.format("%03d", idCounter.getAndIncrement());
    }
}
