package com.company.inventory.domain.event;

import com.company.inventory.messaging.publisher.EventPublisher;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Marker interface for domain events emitted by the reservation aggregate.
 */
public sealed interface DomainEvent
        permits ReservationCreatedEvent, ReservationConfirmedEvent, ReservationCancelledEvent {

    UUID reservationId();
    String orderId();
    OffsetDateTime occurredAt();
    EventType type();
}
