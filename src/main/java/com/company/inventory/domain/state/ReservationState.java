package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.exception.InvalidStateTransitionException;

/**
 * Abstract class for the Reservation. State pattern — abstract state for the {@link Reservation} aggregate.
 * <p>
 * Each concrete state knows which transitions are legal from itself and performs them.
 * The service layer never branches on {@link ReservationStatus} — it calls
 * {@code reservation.confirm()} / {@code reservation.cancel()}, which delegate here.
 * <p>
 * Default implementations throw {@link InvalidStateTransitionException};
 * subclasses override only the transitions they permit.
 */
public abstract class ReservationState {

    public abstract ReservationStatus status();

    public void confirm(Reservation reservation) {
        throw InvalidStateTransitionException
                .of(reservation.getId(), status(), ReservationStatus.CONFIRMED);
    }

    public void cancel(Reservation reservation) {
        throw InvalidStateTransitionException
                .of(reservation.getId(), status(), ReservationStatus.CANCELLED);
    }
}
