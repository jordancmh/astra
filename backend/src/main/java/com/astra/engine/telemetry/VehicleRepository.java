package com.astra.engine.telemetry;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, String> {

    List<Vehicle> findAllByOrderByCargoPriorityDesc();

    List<Vehicle> findByStatusOrderByCargoPriorityDesc(String status);
}
