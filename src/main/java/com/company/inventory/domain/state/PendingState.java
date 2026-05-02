package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;

/**
 * CancelledState inherit from the ReservationState, permits CONFIRMED & CANCELLED
 */

public final class PendingState extends ReservationState {

    @Override public ReservationStatus status() { return ReservationStatus.PENDING; }

    @Override
    public void confirm(Reservation reservation) {
        reservation.changeStatusTo(ReservationStatus.CONFIRMED);
    }

    @Override
    public void cancel(Reservation reservation) {
        reservation.changeStatusTo(ReservationStatus.CANCELLED);
    }
}
