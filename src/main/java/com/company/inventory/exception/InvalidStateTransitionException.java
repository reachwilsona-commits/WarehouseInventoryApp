package com.company.inventory.exception;

import com.company.inventory.domain.entity.ReservationStatus;

import java.util.UUID;
/**
 * Exception class for Invalid State
 */
public class InvalidStateTransitionException extends BusinessException {

    public InvalidStateTransitionException(String message) {
        super(ErrorCode.INVALID_STATE_TRANSITION, message);
    }

    public static InvalidStateTransitionException of(UUID id, ReservationStatus from, ReservationStatus to) {
        return new InvalidStateTransitionException(
                "Reservation " + id + " in state " + from + " cannot transition to " + to);
    }
}
