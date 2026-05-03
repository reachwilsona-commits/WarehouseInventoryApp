package com.company.inventory.messaging.publisher;

import com.company.inventory.domain.event.DomainEvent;

/**
 * Producer side facade. Domain code holds this reference and never sees subscribers.
 * */
public interface EventPublisher {
    void publish(DomainEvent event);
}
