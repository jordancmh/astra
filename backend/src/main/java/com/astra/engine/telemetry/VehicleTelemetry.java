package com.astra.engine.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telemetry_log")
public class VehicleTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "route_id")
    private String routeId;

    @Column(name = "coordinates", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point coordinates;

    @Column(name = "bearing")
    private Double bearing;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public VehicleTelemetry() {
    }

    public VehicleTelemetry(String vehicleId, String routeId, Point coordinates, Double bearing, Double speedKmh, Instant recordedAt) {
        this.vehicleId = vehicleId;
        this.routeId = routeId;
        this.coordinates = coordinates;
        this.bearing = bearing;
        this.speedKmh = speedKmh;
        this.recordedAt = recordedAt;
    }

    @PrePersist
    public void prePersist() {
        if (this.recordedAt == null) {
            this.recordedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getRouteId() {
        return routeId;
    }

    public void setRouteId(String routeId) {
        this.routeId = routeId;
    }

    public Point getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(Point coordinates) {
        this.coordinates = coordinates;
    }

    public Double getBearing() {
        return bearing;
    }

    public void setBearing(Double bearing) {
        this.bearing = bearing;
    }

    public Double getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Double speedKmh) {
        this.speedKmh = speedKmh;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
