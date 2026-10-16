package com.example.outbox.flight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.outbox.flight.domain.Flight;
import com.example.outbox.flight.domain.OutboxEvent;
import com.example.outbox.flight.dto.FlightBookedEvent;
import com.example.outbox.flight.dto.FlightRequest;
import com.example.outbox.flight.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class FlightBookingService {

    private static final Logger log = LoggerFactory.getLogger(FlightBookingService.class);

    private final FlightRepository flightRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public FlightBookingService(FlightRepository flightRepository, ObjectMapper objectMapper) {
        this(flightRepository, objectMapper, Clock.systemUTC());
    }

    FlightBookingService(FlightRepository flightRepository, ObjectMapper objectMapper, Clock clock) {
        this.flightRepository = flightRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public Flight book(FlightRequest request) {
        log.info("book called: departure={} arrival={}", request.departureAirport(), request.arrivalAirport());
        validate(request);

        UUID flightId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = clock.instant();
        Flight flight = new Flight(
                flightId,
                request.departureAirport(),
                request.arrivalAirport(),
                request.departureDateTime(),
                request.arrivalDateTime(),
                now);

        flightRepository.insertFlight(flight);

        FlightBookedEvent event = new FlightBookedEvent(
                eventId,
                "FLIGHT_BOOKED",
                now,
                new FlightBookedEvent.FlightPayload(
                        flight.id(),
                        flight.departureAirport(),
                        flight.arrivalAirport(),
                        flight.departureDateTime(),
                        flight.arrivalDateTime()));

        flightRepository.insertOutboxEvent(event, toJson(event));

        log.info("book completed: flightId={} eventId={}", flightId, eventId);
        return flight;
    }

    @Transactional(readOnly = true)
    public Flight findById(UUID flightId) {
        log.info("findById called: flightId={}", flightId);
        Flight flight = flightRepository.findById(flightId)
                .orElseThrow(() -> new FlightNotFoundException(flightId));
        log.info("findById completed: flightId={}", flightId);
        return flight;
    }

    @Transactional(readOnly = true)
    public List<OutboxEvent> findOutboxEvents() {
        log.info("findOutboxEvents called");
        List<OutboxEvent> events = flightRepository.findAllOutboxEvents();
        log.info("findOutboxEvents completed: count={}", events.size());
        return events;
    }

    private void validate(FlightRequest request) {
        if (request.departureAirport().equalsIgnoreCase(request.arrivalAirport())) {
            throw new IllegalArgumentException("Departure and arrival airports must be different");
        }
        if (!request.arrivalDateTime().isAfter(request.departureDateTime())) {
            throw new IllegalArgumentException("Arrival time must be after departure time");
        }
    }

    private String toJson(FlightBookedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize flight event", exception);
        }
    }
}
