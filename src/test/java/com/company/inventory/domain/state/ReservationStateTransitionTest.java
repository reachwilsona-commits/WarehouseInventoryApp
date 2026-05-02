package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Spec coverage:
 *  - Valid transitions: PENDING→CONFIRMED, PENDING→CANCELLED
 *  - Invalid transitions: CONFIRMED→CANCELLED, CANCELLED→CONFIRMED, CANCELLED→CANCELLED, CONFIRMED→CONFIRMED
 *  - Design pattern coverage: state mutations go through the State classes, not service if/else
 */
class ReservationStateTransitionTest {

    private Reservation newPending() {
        return new Reservation(UUID.randomUUID(), "ORD-1", OffsetDateTime.now(),
                OffsetDateTime.now().plusMinutes(10));
    }

    @Test
    void pending_canBeConfirmed() {
        Reservation r = newPending();
        r.confirm();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void pending_canBeCancelled() {
        Reservation r = newPending();
        r.cancel();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
    }

    @Test
    void confirmed_cannotBeCancelled() {
        Reservation r = newPending();
        r.confirm();
        assertThatThrownBy(r::cancel)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void confirmed_cannotBeConfirmedAgain() {
        Reservation r = newPending();
        r.confirm();
        assertThatThrownBy(r::confirm)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelled_cannotBeConfirmed() {
        Reservation r = newPending();
        r.cancel();
        assertThatThrownBy(r::confirm)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancelled_cannotBeCancelledAgain() {
        Reservation r = newPending();
        r.cancel();
        assertThatThrownBy(r::cancel)
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void factoryReturnsCorrectStateForEachStatus() {
        assertThat(ReservationStateFactory.forStatus(ReservationStatus.PENDING))
                .isInstanceOf(PendingState.class);
        assertThat(ReservationStateFactory.forStatus(ReservationStatus.CONFIRMED))
                .isInstanceOf(ConfirmedState.class);
        assertThat(ReservationStateFactory.forStatus(ReservationStatus.CANCELLED))
                .isInstanceOf(CancelledState.class);
    }
}
