package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.ReservationStatus;

/**
 * ConfirmedState inherit from the ReservationState can have only CONFIRMED
 */
public final class ConfirmedState extends ReservationState {
    @Override public ReservationStatus status() { return ReservationStatus.CONFIRMED; }
}
