package com.astra.engine.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface VehicleTelemetryRepository extends JpaRepository<VehicleTelemetry, UUID> {

    List<VehicleTelemetry> findByVehicleIdOrderByRecordedAtDesc(String vehicleId);
}
