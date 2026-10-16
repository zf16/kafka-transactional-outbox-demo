package com.example.outbox.relay.scheduler;

import com.example.outbox.relay.service.OutboxBatchProcessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxPoller.class);

    private final OutboxBatchProcessor batchProcessor;

    public OutboxPoller(OutboxBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    /** Polls the outbox on a fixed delay and keeps scheduler failures contained. */
    @Scheduled(fixedDelayString = "${outbox.poll-delay:10000}")
    public void poll() {
        log.info("Outbox poll started");
        try {
            int count = batchProcessor.processBatch();
            log.info("Outbox poll completed: processedCount={}", count);
        } catch (RuntimeException exception) {
            log.error("Outbox poll failed; pending rows will be retried on the next poll", exception);
        }
    }
}
