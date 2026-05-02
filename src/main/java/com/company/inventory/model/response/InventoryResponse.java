package com.company.inventory.model.response;

import com.company.inventory.domain.entity.Inventory;

/**
 * Record class for Inventory Response
 * @param sku String
 * @param totalStock int
 * @param availableStock int
 * @param reservedStock int
 */
public record InventoryResponse(
        String sku,
        int totalStock,
        int availableStock,
        int reservedStock
) {
    public static InventoryResponse fromInventory(Inventory inventory) {
        return new InventoryResponse(inventory.getSku(), inventory.getTotalStock(), inventory.getAvailableStock(), inventory.getReservedStock());
    }
}
