package com.example.outbox.flight.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FlightRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requiresEveryRequestField() {
        FlightRequest request = new FlightRequest(null, "", null, null);

        assertThat(validator.validate(request)).hasSize(4);
    }

    @Test
    void acceptsTheAwsSampleShape() {
        FlightRequest request = new FlightRequest(
                "Paris",
                "London",
                Instant.parse("2026-11-01T08:00:00Z"),
                Instant.parse("2026-11-01T09:15:00Z"));

        assertThat(validator.validate(request)).isEmpty();
    }
}
