package com.company.inventory.domain.event;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 *Record class for Reservation Confirmed Event
 * @param reservationId UUID
 * @param orderId String
 * @param occurredAt OffsetDateTime
 */
public record ReservationConfirmedEvent(
        UUID reservationId,
        String orderId,
        OffsetDateTime occurredAt
) implements DomainEvent {

    @Override public EventType type() { return EventType.RESERVATION_CONFIRMED; }
}
