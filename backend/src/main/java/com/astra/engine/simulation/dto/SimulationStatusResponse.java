package com.astra.engine.simulation.dto;

public record SimulationStatusResponse(
        boolean running,
        int vehicleCount,
        long tickCount,
        String message
) {}
