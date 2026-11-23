package com.astra.engine.anomaly;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NetworkAnomalyRepository extends JpaRepository<NetworkAnomaly, String> {

    List<NetworkAnomaly> findByIsActiveTrueOrderByInjectedAtDesc();
}
