package com.company.inventory.messaging;

import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.domain.event.ReservationConfirmedEvent;
import com.company.inventory.messaging.publisher.DomainEventPublisher;
import com.company.inventory.messaging.subscribers.EventSubscriber;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DomainEventPublisherTest {

    @Test
    void publish_fansOutToAllSubscribers() {
        List<DomainEvent> a = new ArrayList<>();
        List<DomainEvent> b = new ArrayList<>();
        DomainEventPublisher publisher = new DomainEventPublisher(List.of(a::add, b::add));

        DomainEvent event = new ReservationConfirmedEvent(
                UUID.randomUUID(), "ORD-1", OffsetDateTime.now());
        publisher.publish(event);

        assertThat(a).containsExactly(event);
        assertThat(b).containsExactly(event);
    }

    @Test
    void failingSubscriber_doesNotPreventOthersFromReceivingEvent() {
        List<DomainEvent> received = new ArrayList<>();
        EventSubscriber explodes = e -> { throw new RuntimeException("boom exception"); };
        DomainEventPublisher publisher =
                new DomainEventPublisher(List.of(explodes, received::add));

        DomainEvent event = new ReservationConfirmedEvent(
                UUID.randomUUID(), "ORD-1", OffsetDateTime.now());
        publisher.publish(event);

        assertThat(received).containsExactly(event);
    }
}
