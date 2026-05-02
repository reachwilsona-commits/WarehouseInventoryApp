package com.company.inventory.log;

import com.company.inventory.domain.entity.ReservationStatus;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Class for logging the audit-trail.
 * Fields: reservationId, orderId, fromState, toState, triggeredBy, timestamp.
 */
@Component
public class StateTransitionLogger {

    private static final Logger log = LoggerFactory.getLogger("audit-trail");

    public enum Trigger { API, EXPIRY_JOB }

    public void logTransition(UUID reservationId, String orderId,
                              ReservationStatus from, ReservationStatus to,
                              Trigger trigger) {
        log.info("reservation_state_transition",
                StructuredArguments.kv("reservationId", reservationId),
                StructuredArguments.kv("orderId",       orderId),
                StructuredArguments.kv("fromState",     from == null ? null : from.name()),
                StructuredArguments.kv("toState",       to.name()),
                StructuredArguments.kv("triggeredBy",   trigger.name()),
                StructuredArguments.kv("timestamp",     OffsetDateTime.now().toString()));
    }
}
