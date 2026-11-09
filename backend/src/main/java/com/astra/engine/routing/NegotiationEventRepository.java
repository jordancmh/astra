package com.astra.engine.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NegotiationEventRepository extends JpaRepository<NegotiationEvent, UUID> {

    List<NegotiationEvent> findByVehicleIdOrderByCreatedAtDesc(String vehicleId);

    List<NegotiationEvent> findByAnomalyIdOrderByCreatedAtDesc(String anomalyId);
}
