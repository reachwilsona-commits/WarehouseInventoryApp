package com.company.inventory.exception;

/**
 * Exception class for Insufficient Stock
 */
public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String sku, int available, int requested) {
        super(ErrorCode.INSUFFICIENT_STOCK,
              "SKU " + sku + " has only " + available + " units available, " + requested + " were requested");
    }
}
