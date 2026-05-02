package com.company.inventory.messaging.subscribers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.domain.entity.ReservationEvents;
import com.company.inventory.repository.ReservationEventRepository;
import org.springframework.stereotype.Component;

/**
 * Subscriber to persist event on Outbox-style
 */
@Component
public class OutboxPersistenceSubscriber implements EventSubscriber {

    private final ReservationEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPersistenceSubscriber(ReservationEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onEvent(DomainEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // If we can't serialize an event we can't durably record it; surface loudly.
            throw new IllegalStateException("Failed to serialize event " + event.type(), e);
        }
        repository.save(new ReservationEvents(event.reservationId(), event.type().name(), payload));
    }
}
