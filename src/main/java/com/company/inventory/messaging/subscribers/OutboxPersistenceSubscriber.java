package com.company.inventory.messaging.subscribers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.domain.entity.ReservationEvents;
import com.company.inventory.repository.ReservationEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Subscriber DomainEvent and  persist into reservation_events - Outbox-style
 */
@Component
public class OutboxPersistenceSubscriber implements EventSubscriber {

    private static final Logger log = LoggerFactory.getLogger("outbox-persist");
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
            throw new IllegalStateException("Failed to serialize event " + event.type(), e);
        }
        repository.save(new ReservationEvents(event.reservationId(), event.type().name(), payload));
        log.debug("Domain Event persisted in to DB with Id {}",event.orderId());
    }
}
