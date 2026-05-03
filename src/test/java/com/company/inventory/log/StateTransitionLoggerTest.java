package com.company.inventory.log;

import com.company.inventory.domain.entity.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

class StateTransitionLoggerTest {

    private final StateTransitionLogger logger = new StateTransitionLogger();

    @Test
    void logTransition_pendingToConfirmed_doesNotThrow() {
        assertThatCode(() ->
                logger.logTransition(
                        UUID.randomUUID(), "ORD-1",
                        ReservationStatus.PENDING, ReservationStatus.CONFIRMED,
                        StateTransitionLogger.Trigger.API))
                .doesNotThrowAnyException();
    }

    @Test
    void logTransition_pendingToCancelled_viaExpiryJob_doesNotThrow() {
        assertThatCode(() ->
                logger.logTransition(
                        UUID.randomUUID(), "ORD-2",
                        ReservationStatus.PENDING, ReservationStatus.CANCELLED,
                        StateTransitionLogger.Trigger.EXPIRY_JOB))
                .doesNotThrowAnyException();
    }

    @Test
    void logTransition_nullFromState_doesNotThrow() {
        assertThatCode(() ->
                logger.logTransition(
                        UUID.randomUUID(), "ORD-3",
                        null, ReservationStatus.PENDING,
                        StateTransitionLogger.Trigger.API))
                .doesNotThrowAnyException();
    }
}