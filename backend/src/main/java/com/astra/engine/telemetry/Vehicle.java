package com.astra.engine.telemetry;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private String id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "current_route_id")
    private String currentRouteId;

    @Column(name = "current_location", columnDefinition = "geometry(Point, 4326)")
    private Point currentLocation;

    @Column(name = "bearing")
    private Double bearing;

    @Column(name = "speed_kmh")
    private Double speedKmh;

    @Column(name = "cargo_priority", nullable = false)
    private Integer cargoPriority;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Vehicle() {
    }

    public Vehicle(String id, String status, String currentRouteId, Point currentLocation,
                   Double bearing, Double speedKmh, Integer cargoPriority) {
        this.id = id;
        this.status = status;
        this.currentRouteId = currentRouteId;
        this.currentLocation = currentLocation;
        this.bearing = bearing;
        this.speedKmh = speedKmh;
        this.cargoPriority = cargoPriority;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCurrentRouteId() {
        return currentRouteId;
    }

    public void setCurrentRouteId(String currentRouteId) {
        this.currentRouteId = currentRouteId;
    }

    public Point getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Point currentLocation) {
        this.currentLocation = currentLocation;
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

    public Integer getCargoPriority() {
        return cargoPriority;
    }

    public void setCargoPriority(Integer cargoPriority) {
        this.cargoPriority = cargoPriority;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
