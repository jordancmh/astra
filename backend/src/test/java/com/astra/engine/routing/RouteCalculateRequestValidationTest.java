package com.astra.engine.routing;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteCalculateRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    @Test
    void validRequest_passesValidation() {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-001", "V-001",
                3.0430, 101.4496,
                3.1579, 101.7116,
                List.of()
        );
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void originOutOfKlangValley_failsValidation() {
        RouteCalculateRequest request = new RouteCalculateRequest(
                "RT-001", "V-001",
                2.5000, 101.4496,
                3.1579, 101.7116,
                List.of()
        );
        assertThat(validator.validate(request)).isNotEmpty();
    }
}
