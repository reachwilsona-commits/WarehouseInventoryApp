package com.company.inventory.domain.state;

import com.company.inventory.domain.entity.ReservationStatus;

import java.util.Map;

/**
 * Reservation State Factory  for Factory pattern
 * Looks up the {@link ReservationState} singleton for a given persisted
 * {@link ReservationStatus}. State objects are stateless, so a single shared
 * instance per status is safe and avoids per-call allocation.
 */
public final class ReservationStateFactory {

    private static final Map<ReservationStatus, ReservationState> STATES = Map.of(
            ReservationStatus.PENDING,   new PendingState(),
            ReservationStatus.CONFIRMED, new ConfirmedState(),
            ReservationStatus.CANCELLED, new CancelledState()
    );

    private ReservationStateFactory() {}

    public static ReservationState forStatus(ReservationStatus status) {
        ReservationState state = STATES.get(status);
        if (state == null) {
            throw new IllegalStateException("No state registered for status " + status);
        }
        return state;
    }
}
