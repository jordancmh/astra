package com.astra.engine.telemetry;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/telemetry", "/api/telemetry"})
public class TelemetryController {

    private final TelemetryService telemetryService;

    public TelemetryController(TelemetryService telemetryService) {
        this.telemetryService = telemetryService;
    }

    @PostMapping
    public ResponseEntity<TelemetryIngestResponse> ingestTelemetry(
            @Valid @RequestBody TelemetryIngestRequest request) {
        TelemetryIngestResponse response = telemetryService.ingest(request);
        return ResponseEntity.ok(response);
    }
}
