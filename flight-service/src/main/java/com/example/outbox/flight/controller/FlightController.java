package com.example.outbox.flight.controller;

import com.example.outbox.flight.domain.Flight;
import com.example.outbox.flight.dto.FlightRequest;
import com.example.outbox.flight.repository.FlightRepository;
import com.example.outbox.flight.service.FlightBookingService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping
public class FlightController {

    private final FlightBookingService bookingService;
    private final FlightRepository flightRepository;

    public FlightController(FlightBookingService bookingService, FlightRepository flightRepository) {
        this.bookingService = bookingService;
        this.flightRepository = flightRepository;
    }

    @PostMapping("/flights")
    @ResponseStatus(HttpStatus.CREATED)
    public Flight book(@Valid @RequestBody FlightRequest request) {
        return bookingService.book(request);
    }

    @GetMapping("/flights/{flightId}")
    public Flight find(@PathVariable UUID flightId) {
        return flightRepository.findById(flightId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Flight not found"));
    }

    @GetMapping("/internal/outbox")
    public List<FlightRepository.OutboxView> outbox() {
        return flightRepository.findPendingEvents();
    }
}
