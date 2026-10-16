package com.example.outbox.relay.service;

import com.example.outbox.relay.model.OutboxEvent;
import com.example.outbox.relay.repository.OutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxBatchProcessor.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final Duration sendTimeout;
    private final Clock clock;

    @Autowired
    public OutboxBatchProcessor(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.topic:flight.events}") String topic,
            @Value("${outbox.batch-size:10}") int batchSize,
            @Value("${outbox.send-timeout:10s}") Duration sendTimeout) {
        this(outboxRepository, kafkaTemplate, topic, batchSize, sendTimeout, Clock.systemUTC());
    }

    OutboxBatchProcessor(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            String topic,
            int batchSize,
            Duration sendTimeout,
            Clock clock) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
        this.clock = clock;
    }

    /** Publishes one batch and leaves failed rows pending for a future poll. */
    @Transactional
    public int processBatch() {
        log.info("processBatch called: batchSize={} topic={}", batchSize, topic);
        List<OutboxEvent> events = outboxRepository.lockNextBatch(batchSize);
        log.info("Polled {} pending outbox event(s)", events.size());
        int processedCount = 0;

        for (OutboxEvent event : events) {
            try {
                log.info("Publishing outbox event: eventId={} flightId={}",
                        event.eventId(), event.flightId());
                var result = kafkaTemplate.send(topic, event.flightId().toString(), event.payload())
                        .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);

                outboxRepository.markProcessed(event.id(), clock.instant());
                processedCount++;

                log.info("Outbox event processed: eventId={} flightId={} topic={} partition={} offset={}",
                        event.eventId(),
                        event.flightId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.error("Publishing interrupted; event remains pending: eventId={} flightId={}",
                        event.eventId(), event.flightId(), exception);
                break;
            } catch (Exception exception) {
                log.error("Publishing failed; event remains pending: eventId={} flightId={}",
                        event.eventId(), event.flightId(), exception);
            }
        }

        log.info("processBatch completed: polledCount={} processedCount={} failedCount={}",
                events.size(), processedCount, events.size() - processedCount);
        return processedCount;
    }
}
