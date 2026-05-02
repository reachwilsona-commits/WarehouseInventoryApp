package com.company.inventory.exception;

import org.springframework.http.HttpStatus;

/**
 * Single source of truth mapped to HTTP status mapping.
 */
public enum ErrorCode {
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    DUPLICATE_ORDER(HttpStatus.OK),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    SKU_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus httpStatus;

    ErrorCode(HttpStatus httpStatus) { this.httpStatus = httpStatus; }

    public HttpStatus httpStatus() { return httpStatus; }
}
