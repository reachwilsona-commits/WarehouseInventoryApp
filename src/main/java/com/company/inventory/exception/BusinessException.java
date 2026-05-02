package com.company.inventory.exception;

/**
 * Root of the application's business-error hierarchy. Each subclass carries an
 * {@link ErrorCode} that the global exception handler maps to the API envelope.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() { return errorCode; }
}
