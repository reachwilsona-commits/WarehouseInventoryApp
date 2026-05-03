package com.company.inventory.messaging;

import com.company.inventory.domain.event.ReservationCancelledEvent;
import com.company.inventory.domain.event.CancellationReason;
import com.company.inventory.domain.event.ReservationConfirmedEvent;
import com.company.inventory.domain.event.ReservationCreatedEvent;
import com.company.inventory.messaging.subscribers.SubscriberLogging;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class SubscriberLoggingTest {

    private final SubscriberLogging subscriber = new SubscriberLogging();

    @Test
    void onEvent_logsCreatedEvent_withoutThrowing() {
        var event = new ReservationCreatedEvent(
                UUID.randomUUID(), "ORD-1", OffsetDateTime.now(),
                OffsetDateTime.now().plusMinutes(10), List.of());

        assertThatCode(() -> subscriber.onEvent(event)).doesNotThrowAnyException();
    }

    @Test
    void onEvent_logsConfirmedEvent_withoutThrowing() {
        var event = new ReservationConfirmedEvent(UUID.randomUUID(), "ORD-2", OffsetDateTime.now());

        assertThatCode(() -> subscriber.onEvent(event)).doesNotThrowAnyException();
    }

    @Test
    void onEvent_logsCancelledEvent_withoutThrowing() {
        var event = new ReservationCancelledEvent(
                UUID.randomUUID(), "ORD-3", OffsetDateTime.now(), CancellationReason.TTL_EXPIRED);

        assertThatCode(() -> subscriber.onEvent(event)).doesNotThrowAnyException();
    }
}
