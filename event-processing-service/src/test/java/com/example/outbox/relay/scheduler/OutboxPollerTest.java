package com.example.outbox.relay.scheduler;

import com.example.outbox.relay.service.OutboxBatchProcessor;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPollerTest {

    @Test
    void delegatesEachPollToTheBatchProcessor() {
        OutboxBatchProcessor processor = mock(OutboxBatchProcessor.class);
        when(processor.processBatch()).thenReturn(1);
        OutboxPoller poller = new OutboxPoller(processor);

        poller.poll();

        verify(processor).processBatch();
    }

    @Test
    void leavesRetryToTheNextPollWhenPublicationFails() {
        OutboxBatchProcessor processor = mock(OutboxBatchProcessor.class);
        when(processor.processBatch()).thenThrow(new RuntimeException("Kafka unavailable"));
        OutboxPoller poller = new OutboxPoller(processor);

        poller.poll();

        verify(processor).processBatch();
    }
}
