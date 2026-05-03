package com.company.inventory.messaging.nats;

import com.company.inventory.config.NatsProperties;
import com.company.inventory.domain.entity.ReservationEvents;
import com.company.inventory.repository.ReservationEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.nats.client.JetStream;
import io.nats.client.PublishOptions;
import io.nats.client.api.PublishAck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class NatsOutboxJobTest {

    private ReservationEventRepository repository;
    private JetStream jetStream;
    private NatsOutboxJob relay;

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        repository = mock(ReservationEventRepository.class);
        jetStream   = mock(JetStream.class);
        NatsProperties props = new NatsProperties(
                "nats://localhost:4222", "RESERVATIONS", true, 50);
        relay = new NatsOutboxJob(repository, jetStream, MAPPER, props);
    }

    @Test
    void run_doesNothing_whenOutboxIsEmpty() throws Exception {
        when(repository.findUnpublishedOrderByIdAsc(any(Pageable.class))).thenReturn(List.of());

        relay.run();

        verifyNoInteractions(jetStream);
        verify(repository, never()).save(any());
    }

    @Test
    void run_publishesEachEvent_andMarksPublished() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationEvents event = outboxRow(id, "RESERVATION_CREATED", 1L,
                createdPayload(id, "ORD-1"));

        PublishAck ack = mockAck(1L, false);
        when(repository.findUnpublishedOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(event));
        when(jetStream.publish(anyString(), any(byte[].class), any(PublishOptions.class)))
                .thenReturn(ack);

        relay.run();

        verify(jetStream).publish(eq("reservations.created"), any(byte[].class), any(PublishOptions.class));
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void run_usesCorrectSubject_forEachEventType() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationEvents created   = outboxRow(id, "RESERVATION_CREATED",   1L, createdPayload(id, "O1"));
        ReservationEvents confirmed = outboxRow(id, "RESERVATION_CONFIRMED",  2L, confirmedPayload(id, "O1"));
        ReservationEvents cancelled = outboxRow(id, "RESERVATION_CANCELLED",  3L, cancelledPayload(id, "O1"));

        PublishAck ack = mockAck(1L, false);
        when(repository.findUnpublishedOrderByIdAsc(any(Pageable.class)))
                .thenReturn(List.of(created, confirmed, cancelled));
        when(jetStream.publish(anyString(), any(byte[].class), any(PublishOptions.class)))
                .thenReturn(ack);

        relay.run();

        verify(jetStream).publish(eq("reservations.created"),   any(byte[].class), any(PublishOptions.class));
        verify(jetStream).publish(eq("reservations.confirmed"), any(byte[].class), any(PublishOptions.class));
        verify(jetStream).publish(eq("reservations.cancelled"), any(byte[].class), any(PublishOptions.class));
    }

    @Test
    void run_isolatesFailure_continuesWithRemainingEvents() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationEvents bad  = outboxRow(id, "RESERVATION_CREATED",  1L, createdPayload(id, "O1"));
        ReservationEvents good = outboxRow(id, "RESERVATION_CONFIRMED", 2L, confirmedPayload(id, "O1"));

        PublishAck goodAck = mockAck(2L, false);
        when(repository.findUnpublishedOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(bad, good));
        when(jetStream.publish(eq("reservations.created"), any(byte[].class), any(PublishOptions.class)))
                .thenThrow(new IOException("NATS timeout"));
        when(jetStream.publish(eq("reservations.confirmed"), any(byte[].class), any(PublishOptions.class)))
                .thenReturn(goodAck);

        relay.run();

        assertThat(bad.getPublishedAt()).isNull();   // failed — not marked
        assertThat(good.getPublishedAt()).isNotNull(); // succeeded
        verify(repository, never()).save(bad);
        verify(repository).save(good);
    }

    @Test
    void run_marksEventPublished_evenWhenNatsSaysDuplicate() throws Exception {
        UUID id = UUID.randomUUID();
        ReservationEvents event = outboxRow(id, "RESERVATION_CONFIRMED", 5L, confirmedPayload(id, "O2"));

        PublishAck dupAck = mockAck(5L, true);
        when(repository.findUnpublishedOrderByIdAsc(any(Pageable.class))).thenReturn(List.of(event));
        when(jetStream.publish(anyString(), any(byte[].class), any(PublishOptions.class)))
                .thenReturn(dupAck); // duplicate=true

        relay.run();

        // A duplicate ack still means NATS has the message — mark as published
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    // --- helpers ---

    private ReservationEvents outboxRow(UUID reservationId, String type, long rowId, String payloadJson) {
        ReservationEvents row = new ReservationEvents(reservationId, type, payloadJson);
        setId(row, rowId);
        return row;
    }

    private void setId(ReservationEvents row, long id) {
        try {
            var field = ReservationEvents.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(row, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String createdPayload(UUID reservationId, String orderId) {
        return String.format(
                "{\"reservationId\":\"%s\",\"orderId\":\"%s\",\"occurredAt\":\"2026-01-01T00:00:00Z\"," +
                "\"expiresAt\":\"2026-01-01T00:10:00Z\",\"items\":[]}",
                reservationId, orderId);
    }

    private String confirmedPayload(UUID reservationId, String orderId) {
        return String.format(
                "{\"reservationId\":\"%s\",\"orderId\":\"%s\",\"occurredAt\":\"2026-01-01T00:05:00Z\"}",
                reservationId, orderId);
    }

    private String cancelledPayload(UUID reservationId, String orderId) {
        return String.format(
                "{\"reservationId\":\"%s\",\"orderId\":\"%s\",\"occurredAt\":\"2026-01-01T00:06:00Z\"," +
                "\"reason\":\"USER_REQUEST\"}",
                reservationId, orderId);
    }

    private PublishAck mockAck(long seq, boolean duplicate) {
        PublishAck ack = mock(PublishAck.class);
        when(ack.getSeqno()).thenReturn(seq);
        when(ack.isDuplicate()).thenReturn(duplicate);
        when(ack.getStream()).thenReturn("RESERVATIONS");
        return ack;
    }
}