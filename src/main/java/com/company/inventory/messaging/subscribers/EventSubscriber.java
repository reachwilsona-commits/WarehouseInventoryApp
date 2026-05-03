package com.company.inventory.messaging.subscribers;

import com.company.inventory.domain.event.DomainEvent;

/**
 * Consumer side Observer pattern. Producers know nothing about subscribers.
 */
public interface EventSubscriber {
    void onEvent(DomainEvent event);
}
