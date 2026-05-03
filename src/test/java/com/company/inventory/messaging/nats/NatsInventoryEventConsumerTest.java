package com.company.inventory.messaging.nats;

import com.company.inventory.domain.event.NatsEventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.Message;
import io.nats.client.impl.NatsJetStreamMetaData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.*;

class NatsInventoryEventConsumerTest {

    private ObjectMapper objectMapper;
    private NatsInventoryEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        consumer = new NatsInventoryEventConsumer(
                mock(Connection.class), mock(JetStream.class), objectMapper);
    }

    @Test
    void onMessage_acksAfterSuccessfulProcessing() throws Exception {
        Message msg = validMessage(42L, 1L);

        invokeOnMessage(msg);

        verify(msg).ack();
        verify(msg, never()).nak();
    }

    @Test
    void onMessage_skipsAndAcks_forAlreadySeenSequence() throws Exception {
        Message msg1 = validMessage(10L, 1L);
        Message msg2 = validMessage(10L, 1L); // same seq number

        invokeOnMessage(msg1);
        invokeOnMessage(msg2);

        // Both calls must ack; msg2 must be skipped without reprocessing
        verify(msg1).ack();
        verify(msg2).ack();
        verify(msg2, never()).nak();
    }

    @Test
    void onMessage_naks_whenDeserializationFails() throws Exception {
        Message msg = messageWithBody(99L, 1L, "not-valid-json{{".getBytes(StandardCharsets.UTF_8));

        invokeOnMessage(msg);

        verify(msg).nak();
        verify(msg, never()).ack();
    }

    @Test
    void onMessage_processesAndAcks_forRedeliveredMessage() throws Exception {
        // deliveredCount=3 simulates a message that failed twice before
        Message msg = validMessage(77L, 3L);

        invokeOnMessage(msg);

        verify(msg).ack();
        verify(msg, never()).nak();
    }

    // --- helpers ---

    private Message validMessage(long seq, long deliveryCount) throws Exception {
        NatsEventEnvelope envelope = new NatsEventEnvelope(
                "RESERVATION_CONFIRMED",
                "550e8400-e29b-41d4-a716-446655440000",
                "ORD-99",
                "2026-01-01T10:00:00Z",
                objectMapper.readTree("{}")
        );
        byte[] body = objectMapper.writeValueAsBytes(envelope);
        return messageWithBody(seq, deliveryCount, body);
    }

    private Message messageWithBody(long seq, long deliveryCount, byte[] body) {
        Message msg = mock(Message.class);
        NatsJetStreamMetaData meta = mock(NatsJetStreamMetaData.class);
        when(meta.streamSequence()).thenReturn(seq);
        when(meta.deliveredCount()).thenReturn(deliveryCount);
        when(msg.metaData()).thenReturn(meta);
        when(msg.getData()).thenReturn(body);
        when(msg.getSubject()).thenReturn("reservations.confirmed");
        return msg;
    }

    /** Invokes the private onMessage method via reflection (it is a MessageHandler callback). */
    private void invokeOnMessage(Message msg) throws Exception {
        Method m = NatsInventoryEventConsumer.class.getDeclaredMethod("onMessage", Message.class);
        m.setAccessible(true);
        m.invoke(consumer, msg);
    }
}