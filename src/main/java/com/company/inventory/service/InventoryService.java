package com.company.inventory.service;

import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.exception.SkuNotFoundException;
import com.company.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for Inventory
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public Inventory getBySku(String sku) {
        return inventoryRepository.findById(sku)
                .orElseThrow(() -> new SkuNotFoundException(sku));
    }
}
