package com.example.outbox.flight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.outbox.flight.domain.Flight;
import com.example.outbox.flight.dto.FlightBookedEvent;
import com.example.outbox.flight.dto.FlightRequest;
import com.example.outbox.flight.repository.FlightRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class FlightBookingService {

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

        return flight;
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
