package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class TelemetryService {

    private final VehicleTelemetryRepository telemetryRepository;

    public TelemetryService(VehicleTelemetryRepository telemetryRepository) {
        this.telemetryRepository = telemetryRepository;
    }

    @Transactional
    public TelemetryIngestResponse ingest(TelemetryIngestRequest request) {
        Point coordinates = GeometryUtils.createPoint(request.longitude(), request.latitude());
        VehicleTelemetry entity = new VehicleTelemetry(
                request.vehicleId(),
                request.routeId(),
                coordinates,
                request.bearing(),
                request.speedKmh(),
                Instant.now()
        );
        VehicleTelemetry saved = telemetryRepository.save(entity);

        return new TelemetryIngestResponse(
                saved.getVehicleId(),
                "NOMINAL",
                null,
                saved.getRecordedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<VehicleStatusDto> getFleetState(String vehicleId) {
        if (vehicleId != null && !vehicleId.isBlank()) {
            return telemetryRepository.findFirstByVehicleIdOrderByRecordedAtDesc(vehicleId.trim())
                    .map(this::toStatusDto)
                    .map(List::of)
                    .orElseGet(List::of);
        }
        return telemetryRepository.findLatestTelemetryForAllVehicles().stream()
                .map(this::toStatusDto)
                .toList();
    }

    private VehicleStatusDto toStatusDto(VehicleTelemetry entity) {
        double lat = entity.getCoordinates() != null ? entity.getCoordinates().getY() : 0.0;
        double lng = entity.getCoordinates() != null ? entity.getCoordinates().getX() : 0.0;
        return new VehicleStatusDto(
                entity.getVehicleId(),
                entity.getRouteId(),
                lat,
                lng,
                entity.getBearing(),
                entity.getSpeedKmh(),
                "NOMINAL",
                entity.getRecordedAt()
        );
    }
}
