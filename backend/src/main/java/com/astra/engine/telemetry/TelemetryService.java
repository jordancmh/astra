package com.astra.engine.telemetry;

import com.astra.engine.common.GeometryUtils;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

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
}
