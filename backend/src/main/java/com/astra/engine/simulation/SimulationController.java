package com.astra.engine.simulation;

import com.astra.engine.simulation.dto.SimulationStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/simulation", "/api/simulation"})
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping("/seed")
    public ResponseEntity<SimulationStatusResponse> seedFleet() {
        return ResponseEntity.ok(simulationService.seedFleet());
    }

    @PostMapping("/start")
    public ResponseEntity<SimulationStatusResponse> startSimulation() {
        return ResponseEntity.ok(simulationService.startSimulation());
    }

    @PostMapping("/stop")
    public ResponseEntity<SimulationStatusResponse> stopSimulation() {
        return ResponseEntity.ok(simulationService.stopSimulation());
    }

    @DeleteMapping("/reset")
    public ResponseEntity<SimulationStatusResponse> resetSimulation() {
        return ResponseEntity.ok(simulationService.resetSimulation());
    }

    @GetMapping("/status")
    public ResponseEntity<SimulationStatusResponse> getStatus() {
        return ResponseEntity.ok(simulationService.getStatus());
    }
}
