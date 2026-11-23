package com.astra.engine.telemetry.dto;

public record FleetSummaryDto(
        int total,
        int nominal,
        int rerouting,
        int disrupted
) {}
