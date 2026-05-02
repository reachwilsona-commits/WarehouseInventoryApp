package com.company.inventory.model.request;

/**
 * Record class for Item with request
 * @param sku String
 * @param quantity int
 */
public record RequestedItem(String sku, int quantity) {}
