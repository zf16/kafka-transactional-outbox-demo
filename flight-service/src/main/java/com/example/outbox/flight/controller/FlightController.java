package com.example.outbox.flight.controller;

import com.example.outbox.flight.dto.FlightRequest;
import com.example.outbox.flight.dto.FlightResponse;
import com.example.outbox.flight.dto.OutboxEventResponse;
import com.example.outbox.flight.service.FlightBookingService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Version 1 HTTP API for booking and inspecting flights.
 *
 * <p>The controller is deliberately limited to HTTP concerns. Business logic and
 * persistence are delegated to {@link FlightBookingService}.</p>
 */
@RestController
@RequestMapping("/api/v1")
public class FlightController {

    private static final Logger log = LoggerFactory.getLogger(FlightController.class);

    private final FlightBookingService bookingService;

    public FlightController(FlightBookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** Books a flight and atomically creates its outbox event. */
    @PostMapping("/flights")
    public ResponseEntity<FlightResponse> book(@Valid @RequestBody FlightRequest request) {
        log.info("Booking flight request received: departure={} arrival={}",
                request.departureAirport(), request.arrivalAirport());
        FlightResponse response = FlightResponse.from(bookingService.book(request));
        log.info("Booking flight request completed: flightId={}", response.id());
        return ResponseEntity.created(URI.create("/api/v1/flights/" + response.id())).body(response);
    }

    /** Returns one flight by its public identifier. */
    @GetMapping("/flights/{flightId}")
    public FlightResponse find(@PathVariable UUID flightId) {
        log.info("Get flight request received: flightId={}", flightId);
        FlightResponse response = FlightResponse.from(bookingService.findById(flightId));
        log.info("Get flight request completed: flightId={}", flightId);
        return response;
    }

    /**
     * Returns retained outbox rows for demo inspection, including their processing timestamp.
     */
    @GetMapping("/internal/outbox-events")
    public List<OutboxEventResponse> outbox() {
        log.info("List outbox events request received");
        List<OutboxEventResponse> response = bookingService.findOutboxEvents().stream()
                .map(OutboxEventResponse::from)
                .toList();
        log.info("List outbox events request completed: count={}", response.size());
        return response;
    }
}
