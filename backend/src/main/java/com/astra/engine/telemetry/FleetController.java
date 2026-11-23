package com.astra.engine.telemetry;

import com.astra.engine.telemetry.dto.FleetStateResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/fleet", "/api/fleet"})
public class FleetController {

    private final FleetService fleetService;

    public FleetController(FleetService fleetService) {
        this.fleetService = fleetService;
    }

    @GetMapping
    public ResponseEntity<FleetStateResponse> getFleetState() {
        return ResponseEntity.ok(fleetService.getFleetState());
    }
}
