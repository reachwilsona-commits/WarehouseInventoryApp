package com.company.inventory.exception;

import java.util.UUID;

/**
 * Exception class for Reservation Not Found
 */
public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException(UUID id) {
        super(ErrorCode.RESERVATION_NOT_FOUND, "Reservation " + id + " was not found");
    }
}
