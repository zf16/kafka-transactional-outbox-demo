package com.example.outbox.payment.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.outbox.payment.dto.FlightBookedEvent;
import com.example.outbox.payment.service.PaymentEventHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventListener.class);

    private final ObjectMapper objectMapper;
    private final PaymentEventHandler handler;

    public PaymentEventListener(ObjectMapper objectMapper, PaymentEventHandler handler) {
        this.objectMapper = objectMapper;
        this.handler = handler;
    }

    @KafkaListener(topics = "${payment.topic:flight.events}")
    public void onMessage(ConsumerRecord<String, String> record) {
        FlightBookedEvent event = deserialize(record.value());
        log.debug("Received eventId={} topic={} partition={} offset={}",
                event.eventId(), record.topic(), record.partition(), record.offset());
        handler.handle(event);
    }

    private FlightBookedEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, FlightBookedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid flight event JSON", exception);
        }
    }
}
