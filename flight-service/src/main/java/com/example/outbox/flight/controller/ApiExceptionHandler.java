package com.example.outbox.flight.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.outbox.flight.service.FlightNotFoundException;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        detail.setType(URI.create("https://example.com/problems/invalid-flight-request"));
        detail.setTitle("Invalid flight request");
        return detail;
    }

    @ExceptionHandler(FlightNotFoundException.class)
    ProblemDetail handleNotFound(FlightNotFoundException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        detail.setType(URI.create("https://example.com/problems/flight-not-found"));
        detail.setTitle("Flight not found");
        return detail;
    }
}
