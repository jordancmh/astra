package com.astra.engine.anomaly;

import com.astra.engine.anomaly.dto.AnomalyClearResponse;
import com.astra.engine.anomaly.dto.AnomalyDto;
import com.astra.engine.anomaly.dto.AnomalyInjectRequest;
import com.astra.engine.anomaly.dto.AnomalyInjectResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/anomalies", "/api/anomalies"})
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    @PostMapping
    public ResponseEntity<AnomalyInjectResponse> injectAnomaly(@Valid @RequestBody AnomalyInjectRequest request) {
        AnomalyInjectResponse response = anomalyService.injectAnomaly(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AnomalyDto>> getActiveAnomalies() {
        return ResponseEntity.ok(anomalyService.getActiveAnomalies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnomalyDto> getAnomalyById(@PathVariable String id) {
        return anomalyService.getAnomalyById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAnomaly(@PathVariable String id) {
        boolean deleted = anomalyService.deleteAnomaly(id);
        if (deleted) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<AnomalyClearResponse> clearAllAnomalies() {
        return ResponseEntity.ok(anomalyService.clearAllAnomalies());
    }
}
