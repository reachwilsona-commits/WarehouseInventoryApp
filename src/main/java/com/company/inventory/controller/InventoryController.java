package com.company.inventory.controller;

import com.company.inventory.model.response.ApiResponse;
import com.company.inventory.model.response.InventoryResponse;
import com.company.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller class for Inventory
 */
@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /**
     * Method to get SKU details
     * @param sku String
     * @return InventoryResponse
     */
    @GetMapping("/{sku}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getSkuDetails(@PathVariable String sku) {
        return ResponseEntity.ok(
                ApiResponse.success(InventoryResponse.fromInventory(inventoryService.getBySku(sku))));
    }
}
