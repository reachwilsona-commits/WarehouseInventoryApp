package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.ReservationStatus;

/**
 * CancelledState inherit from the ReservationState can have only CANCELLED
 */
public final class CancelledState extends ReservationState {
    @Override public ReservationStatus status() { return ReservationStatus.CANCELLED; }
}
