package com.example.outbox.relay.service;

import com.example.outbox.relay.model.OutboxEvent;
import com.example.outbox.relay.repository.OutboxRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
public class OutboxBatchProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxBatchProcessor.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxBatchProcessor(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${outbox.topic:flight.events}") String topic,
            @Value("${outbox.batch-size:10}") int batchSize,
            @Value("${outbox.send-timeout:10s}") Duration sendTimeout) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Transactional
    public int processBatch() {
        List<OutboxEvent> events = outboxRepository.lockNextBatch(batchSize);

        for (OutboxEvent event : events) {
            try {
                var result = kafkaTemplate.send(topic, event.flightId().toString(), event.payload())
                        .get(sendTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);

                outboxRepository.delete(event.id());

                log.info("Published eventId={} flightId={} topic={} partition={} offset={}",
                        event.eventId(),
                        event.flightId(),
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new OutboxPublicationException(event.eventId(), exception);
            } catch (Exception exception) {
                throw new OutboxPublicationException(event.eventId(), exception);
            }
        }

        return events.size();
    }
}
