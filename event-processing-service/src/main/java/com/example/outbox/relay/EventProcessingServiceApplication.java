package com.example.outbox.relay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EventProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EventProcessingServiceApplication.class, args);
    }
}
