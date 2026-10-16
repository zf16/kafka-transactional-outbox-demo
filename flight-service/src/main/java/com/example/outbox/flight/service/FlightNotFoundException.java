package com.example.outbox.flight.service;

import java.util.UUID;

/** Raised when a requested flight does not exist. */
public class FlightNotFoundException extends RuntimeException {

    public FlightNotFoundException(UUID flightId) {
        super("Flight " + flightId + " was not found");
    }
}
