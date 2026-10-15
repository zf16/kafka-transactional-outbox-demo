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

    @Scheduled(fixedDelayString = "${outbox.poll-delay:1000}")
    public void poll() {
        try {
            int count = batchProcessor.processBatch();
            if (count > 0) {
                log.info("Processed {} outbox event(s)", count);
            }
        } catch (RuntimeException exception) {
            log.warn("Outbox publication failed; rows will be retried on the next poll: {}",
                    exception.getMessage());
        }
    }
}
