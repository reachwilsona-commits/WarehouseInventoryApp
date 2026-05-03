package com.company.inventory.messaging;

import com.company.inventory.domain.event.CancellationReason;
import com.company.inventory.domain.event.ReservationCancelledEvent;
import com.company.inventory.domain.event.ReservationConfirmedEvent;
import com.company.inventory.domain.event.ReservationCreatedEvent;
import com.company.inventory.messaging.subscribers.OutboxPersistenceSubscriber;
import com.company.inventory.repository.ReservationEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


class OutboxPersistenceSubscriberTest {

    private ReservationEventRepository repository;
    private ObjectMapper objectMapper;
    private OutboxPersistenceSubscriber subscriber;

    @BeforeEach
    void setUp() {
        repository   = mock(ReservationEventRepository.class);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        subscriber = new OutboxPersistenceSubscriber(repository, objectMapper);
    }

    @Test
    void onEvent_persistsCreatedEvent_withCorrectType() {
        var event = new ReservationCreatedEvent(
                UUID.randomUUID(), "ORD-1", OffsetDateTime.now(),
                OffsetDateTime.now().plusMinutes(10), List.of());

        subscriber.onEvent(event);

        verify(repository).save(argThat(row ->
                "RESERVATION_CREATED".equals(row.getEventType()) &&
                event.reservationId().equals(row.getReservationId()) &&
                row.getPayload() != null));
    }

    @Test
    void onEvent_persistsConfirmedEvent_withCorrectType() {
        var event = new ReservationConfirmedEvent(UUID.randomUUID(), "ORD-2", OffsetDateTime.now());

        subscriber.onEvent(event);

        verify(repository).save(argThat(row ->
                "RESERVATION_CONFIRMED".equals(row.getEventType())));
    }

    @Test
    void onEvent_persistsCancelledEvent_withCorrectType() {
        var event = new ReservationCancelledEvent(
                UUID.randomUUID(), "ORD-3", OffsetDateTime.now(), CancellationReason.USER_REQUEST);

        subscriber.onEvent(event);

        verify(repository).save(argThat(row ->
                "RESERVATION_CANCELLED".equals(row.getEventType())));
    }

    @Test
    void onEvent_throwsIllegalStateException_whenSerializationFails() throws JsonProcessingException {
        ObjectMapper broken = mock(ObjectMapper.class);
        when(broken.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("simulated failure") {});
        subscriber = new OutboxPersistenceSubscriber(repository, broken);

        var event = new ReservationConfirmedEvent(UUID.randomUUID(), "ORD-X", OffsetDateTime.now());

        assertThatThrownBy(() -> subscriber.onEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize event");
    }
}