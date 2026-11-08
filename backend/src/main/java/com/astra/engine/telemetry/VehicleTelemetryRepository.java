package com.astra.engine.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, UUID> {

    List<VehicleTelemetry> findByVehicleIdOrderByRecordedAtDesc(String vehicleId);

    Optional<VehicleTelemetry> findFirstByVehicleIdOrderByRecordedAtDesc(String vehicleId);

    @Query("SELECT t FROM VehicleTelemetry t WHERE t.recordedAt = " +
           "(SELECT MAX(t2.recordedAt) FROM VehicleTelemetry t2 WHERE t2.vehicleId = t.vehicleId)")
    List<VehicleTelemetry> findLatestTelemetryForAllVehicles();
}
