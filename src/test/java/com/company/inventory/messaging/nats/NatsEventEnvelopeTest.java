package com.company.inventory.messaging.nats;

import com.company.inventory.domain.event.NatsEventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


class NatsEventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Test
    void serializedEnvelope_containsAllRequiredFields() throws Exception {
        JsonNode innerPayload = objectMapper.readTree(
                "{\"expiresAt\":\"2026-01-01T10:00:00Z\",\"items\":[{\"sku\":\"A100\",\"quantity\":5}]}");

        NatsEventEnvelope envelope = new NatsEventEnvelope(
                "RESERVATION_CREATED",
                "550e8400-e29b-41d4-a716-446655440000",
                "ORD-99",
                "2026-01-01T10:00:00Z",
                innerPayload
        );

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("eventType")).isTrue();
        assertThat(node.has("reservationId")).isTrue();
        assertThat(node.has("orderId")).isTrue();
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.has("payload")).isTrue();
    }

    @Test
    void roundTrip_preservesAllFieldValues() throws Exception {
        JsonNode innerPayload = objectMapper.readTree("{\"reason\":\"USER_REQUEST\"}");

        NatsEventEnvelope original = new NatsEventEnvelope(
                "RESERVATION_CANCELLED",
                "550e8400-e29b-41d4-a716-446655440001",
                "ORD-42",
                "2026-05-01T08:30:00Z",
                innerPayload
        );

        String json = objectMapper.writeValueAsString(original);
        NatsEventEnvelope restored = objectMapper.readValue(json, NatsEventEnvelope.class);

        assertThat(restored.eventType()).isEqualTo("RESERVATION_CANCELLED");
        assertThat(restored.reservationId()).isEqualTo("550e8400-e29b-41d4-a716-446655440001");
        assertThat(restored.orderId()).isEqualTo("ORD-42");
        assertThat(restored.timestamp()).isEqualTo("2026-05-01T08:30:00Z");
        assertThat(restored.payload().path("reason").asText()).isEqualTo("USER_REQUEST");
    }

    @Test
    void nestedPayload_isEmbeddedAsObject_notString() throws Exception {
        JsonNode innerPayload = objectMapper.readTree("{\"sku\":\"B200\"}");

        NatsEventEnvelope envelope = new NatsEventEnvelope(
                "RESERVATION_CONFIRMED", "uuid", "ORD-1", "ts", innerPayload);

        String json = objectMapper.writeValueAsString(envelope);
        JsonNode root = objectMapper.readTree(json);

        // payload must be an object node, not a quoted string
        assertThat(root.path("payload").isObject()).isTrue();
        assertThat(root.path("payload").path("sku").asText()).isEqualTo("B200");
    }
}