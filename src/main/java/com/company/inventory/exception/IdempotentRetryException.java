package com.company.inventory.exception;

/**
 * Class to check the Idempotency and throw the exception
 */
public class IdempotentRetryException extends RuntimeException {
    private final String orderId;
    public IdempotentRetryException(String orderId, Throwable cause) {
        super("orderId already exists: " + orderId, cause);
        this.orderId = orderId;
    }
    public String orderId() { return orderId; }
}