package com.company.inventory.messaging.subscribers;

import com.company.inventory.domain.event.DomainEvent;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Logs each domain event as a log.
 * Downstream log consumers can subscribe to either stream.
 */
@Component
public class SubscriberLogging implements EventSubscriber {

    private static final Logger log = LoggerFactory.getLogger("domain-events");

    @Override
    public void onEvent(DomainEvent event) {
        log.info("domain_event",
                StructuredArguments.kv("eventType",     event.type().name()),
                StructuredArguments.kv("reservationId", event.reservationId()),
                StructuredArguments.kv("orderId",       event.orderId()),
                StructuredArguments.kv("occurredAt",    event.occurredAt()));
    }
}
