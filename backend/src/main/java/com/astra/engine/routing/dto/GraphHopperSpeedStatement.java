package com.astra.engine.routing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GraphHopperSpeedStatement(
        @JsonProperty("if") String ifCondition,
        @JsonProperty("multiply_by") String multiplyBy
) {}
