package com.company.inventory.messaging.publisher;

import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.messaging.subscribers.EventSubscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Class to Domain Event publisher.  Implementation of the Observer pattern.
 * <p>
 * Subscribers are auto-discovered via Spring constructor injection of
 * {@code List<EventSubscriber>}. A subscriber that throws is logged but does not
 * prevent the others from receiving the event — events are best-effort fan-out at
 * this layer; durability is provided by the outbox row written upstream.
 */
@Component
public class DomainEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final List<EventSubscriber> subscribers;

    public DomainEventPublisher(List<EventSubscriber> subscribers) {
        this.subscribers = subscribers;
        log.info("DomainEventPublisher wired with {} subscribers: {}",
                subscribers.size(),
                subscribers.stream().map(s -> s.getClass().getSimpleName()).toList());
    }

    @Override
    public void publish(DomainEvent event) {
        for (EventSubscriber subscriber : subscribers) {
            try {
                subscriber.onEvent(event);
            } catch (RuntimeException ex) {
                log.error("Subscriber {} failed to process event {}",
                        subscriber.getClass().getSimpleName(), event.type(), ex);
            }
        }
    }
}
