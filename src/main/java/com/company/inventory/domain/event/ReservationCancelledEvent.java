package com.company.inventory.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Record class for Reservation Cancelled Event
 * @param reservationId UUID
 * @param orderId String
 * @param occurredAt OffsetDateTime
 * @param reason CancellationReason
 */
public record ReservationCancelledEvent(
        UUID reservationId,
        String orderId,
        OffsetDateTime occurredAt,
        CancellationReason reason
) implements DomainEvent {

    @Override public EventType type() { return EventType.RESERVATION_CANCELLED; }
}
