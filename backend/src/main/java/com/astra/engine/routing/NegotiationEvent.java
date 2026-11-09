package com.astra.engine.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "negotiation_event")
public class NegotiationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "anomaly_id")
    private String anomalyId;

    @Column(name = "vehicle_id", nullable = false)
    private String vehicleId;

    @Column(name = "affected_routes")
    private String affectedRoutes;

    @Column(name = "event_message", nullable = false, length = 1000)
    private String eventMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public NegotiationEvent() {
    }

    public NegotiationEvent(String anomalyId, String vehicleId, String affectedRoutes, String eventMessage, Instant createdAt) {
        this.anomalyId = anomalyId;
        this.vehicleId = vehicleId;
        this.affectedRoutes = affectedRoutes;
        this.eventMessage = eventMessage;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getAnomalyId() {
        return anomalyId;
    }

    public void setAnomalyId(String anomalyId) {
        this.anomalyId = anomalyId;
    }

    public String getVehicleId() {
        return vehicleId;
    }

    public void setVehicleId(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public String getAffectedRoutes() {
        return affectedRoutes;
    }

    public void setAffectedRoutes(String affectedRoutes) {
        this.affectedRoutes = affectedRoutes;
    }

    public String getEventMessage() {
        return eventMessage;
    }

    public void setEventMessage(String eventMessage) {
        this.eventMessage = eventMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
