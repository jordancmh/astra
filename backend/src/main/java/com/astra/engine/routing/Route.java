package com.astra.engine.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;

import java.time.Instant;

@Entity
@Table(name = "route")
public class Route {

    @Id
    private String id;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "origin_point", columnDefinition = "geometry(Point, 4326)")
    private Point originPoint;

    @Column(name = "destination_point", columnDefinition = "geometry(Point, 4326)")
    private Point destinationPoint;

    @Column(name = "path_geometry", columnDefinition = "geometry(LineString, 4326)", nullable = false)
    private LineString pathGeometry;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Column(name = "estimated_time_ms")
    private Long estimatedTimeMs;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Route() {
    }

    public Route(String id, String status, Point originPoint, Point destinationPoint,
                 LineString pathGeometry, Double distanceMeters, Long estimatedTimeMs) {
        this.id = id;
        this.status = status;
        this.originPoint = originPoint;
        this.destinationPoint = destinationPoint;
        this.pathGeometry = pathGeometry;
        this.distanceMeters = distanceMeters;
        this.estimatedTimeMs = estimatedTimeMs;
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

    public Point getOriginPoint() {
        return originPoint;
    }

    public void setOriginPoint(Point originPoint) {
        this.originPoint = originPoint;
    }

    public Point getDestinationPoint() {
        return destinationPoint;
    }

    public void setDestinationPoint(Point destinationPoint) {
        this.destinationPoint = destinationPoint;
    }

    public LineString getPathGeometry() {
        return pathGeometry;
    }

    public void setPathGeometry(LineString pathGeometry) {
        this.pathGeometry = pathGeometry;
    }

    public Double getDistanceMeters() {
        return distanceMeters;
    }

    public void setDistanceMeters(Double distanceMeters) {
        this.distanceMeters = distanceMeters;
    }

    public Long getEstimatedTimeMs() {
        return estimatedTimeMs;
    }

    public void setEstimatedTimeMs(Long estimatedTimeMs) {
        this.estimatedTimeMs = estimatedTimeMs;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
