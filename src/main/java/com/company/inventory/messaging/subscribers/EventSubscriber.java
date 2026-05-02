package com.company.inventory.messaging.subscribers;

import com.company.inventory.domain.event.DomainEvent;

/**
 * Observer pattern — anything that wants to react to domain events implements this and is
 * picked up automatically by the Spring container; producers know nothing about subscribers,
 * so adding a NATS adapter (or a Kafka one, or another logger) requires zero changes to
 * the reservation service.
 */
public interface EventSubscriber {
    void onEvent(DomainEvent event);
}
