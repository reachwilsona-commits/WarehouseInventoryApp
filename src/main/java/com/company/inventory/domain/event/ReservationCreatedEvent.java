package com.company.inventory.domain.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Record class for Reservation Creataed Event
 * @param reservationId
 * @param orderId
 * @param occurredAt
 * @param expiresAt
 * @param items
 */
public record ReservationCreatedEvent(
        UUID reservationId,
        String orderId,
        OffsetDateTime occurredAt,
        OffsetDateTime expiresAt,
        List<Item> items
) implements DomainEvent {

    @Override public EventType type() { return EventType.RESERVATION_CREATED; }

    public record Item(String sku, int quantity) {}
}
