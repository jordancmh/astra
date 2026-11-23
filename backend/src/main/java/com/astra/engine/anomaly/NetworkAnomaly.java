package com.astra.engine.anomaly;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;

@Entity
@Table(name = "network_anomaly")
public class NetworkAnomaly {

    @Id
    private String id;

    @Column(name = "anomaly_type", nullable = false)
    private String anomalyType;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "center_point", columnDefinition = "geometry(Point, 4326)", nullable = false)
    private Point centerPoint;

    @Column(name = "bounding_box", columnDefinition = "geometry(Polygon, 4326)", nullable = false)
    private Polygon boundingBox;

    @Column(name = "radius_meters")
    private Double radiusMeters;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "injected_at", nullable = false)
    private Instant injectedAt;

    public NetworkAnomaly() {
    }

    public NetworkAnomaly(String id, String anomalyType, String severity, Point centerPoint,
                          Polygon boundingBox, Double radiusMeters, boolean isActive, Instant injectedAt) {
        this.id = id;
        this.anomalyType = anomalyType;
        this.severity = severity;
        this.centerPoint = centerPoint;
        this.boundingBox = boundingBox;
        this.radiusMeters = radiusMeters;
        this.isActive = isActive;
        this.injectedAt = injectedAt != null ? injectedAt : Instant.now();
    }

    @PrePersist
    public void onPrePersist() {
        if (this.injectedAt == null) {
            this.injectedAt = Instant.now();
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public void setAnomalyType(String anomalyType) {
        this.anomalyType = anomalyType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Point getCenterPoint() {
        return centerPoint;
    }

    public void setCenterPoint(Point centerPoint) {
        this.centerPoint = centerPoint;
    }

    public Polygon getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(Polygon boundingBox) {
        this.boundingBox = boundingBox;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Instant getInjectedAt() {
        return injectedAt;
    }

    public void setInjectedAt(Instant injectedAt) {
        this.injectedAt = injectedAt;
    }
}
