package com.company.inventory.domain.event;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Record for NATS event subject.
 */
public record NatsEventEnvelope(
        String eventType,
        String reservationId,
        String orderId,
        String timestamp,
        JsonNode payload
) {}