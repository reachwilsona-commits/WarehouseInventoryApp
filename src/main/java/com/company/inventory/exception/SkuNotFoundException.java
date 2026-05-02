package com.company.inventory.exception;

/**
 * Exception class for SKU Not Found
 */
public class SkuNotFoundException extends BusinessException {
    public SkuNotFoundException(String sku) {
        super(ErrorCode.SKU_NOT_FOUND, "SKU " + sku + " does not exist in inventory");
    }
}
