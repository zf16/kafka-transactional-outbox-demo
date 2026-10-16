package com.example.outbox.relay.service;

import com.example.outbox.relay.model.OutboxEvent;
import com.example.outbox.relay.repository.OutboxRepository;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxBatchProcessorTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

    @Test
    void marksAnAcknowledgedEventAsProcessed() {
        OutboxEvent event = event();
        @SuppressWarnings("unchecked")
        SendResult<String, String> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send("flight.events", event.flightId().toString(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(metadata);

        int processed = processor().processBatch();

        assertEquals(1, processed);
        verify(repository).markProcessed(event.id(), NOW);
    }

    @Test
    void logsAndLeavesAFailedEventPending() {
        OutboxEvent event = event();
        CompletableFuture<SendResult<String, String>> failedSend = new CompletableFuture<>();
        failedSend.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(repository.lockNextBatch(10)).thenReturn(List.of(event));
        when(kafkaTemplate.send("flight.events", event.flightId().toString(), event.payload()))
                .thenReturn(failedSend);

        int processed = processor().processBatch();

        assertEquals(0, processed);
        verify(repository, never()).markProcessed(event.id(), NOW);
    }

    private OutboxBatchProcessor processor() {
        return new OutboxBatchProcessor(
                repository,
                kafkaTemplate,
                "flight.events",
                10,
                Duration.ofSeconds(1),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutboxEvent event() {
        return new OutboxEvent(1L, UUID.randomUUID(), UUID.randomUUID(), "{}");
    }
}
