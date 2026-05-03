package com.company.inventory.service;

import com.company.inventory.cache.InventoryCacheService;
import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.exception.SkuNotFoundException;
import com.company.inventory.model.response.InventoryResponse;
import com.company.inventory.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service class for Inventory
 */
@Service
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryCacheService cacheService;

    public InventoryService(InventoryRepository inventoryRepository,
                             InventoryCacheService cacheService) {
        this.inventoryRepository = inventoryRepository;
        this.cacheService = cacheService;
    }

    @Transactional(readOnly = true)
    public Inventory getBySku(String sku) {
        return inventoryRepository.findById(sku)
                .orElseThrow(() -> new SkuNotFoundException(sku));
    }

    /**
     * Method to get Inventory Response by SKU
     * This API first checks Redis for inventory data. If it’s not there, it reads from PostgreSQL,
     * saves it to Redis, and returns the result.
     * If Redis is unavailable, it automatically uses PostgreSQL instead.
     */
    @Transactional(readOnly = true)
    public InventoryResponse getInventoryBySku(String sku) {
        return cacheService.get(sku).orElseGet(() -> {
            Inventory inv = inventoryRepository.findById(sku)
                    .orElseThrow(() -> new SkuNotFoundException(sku));
            InventoryResponse response = InventoryResponse.fromInventory(inv);
            cacheService.put(sku, response);
            return response;
        });
    }

    /**
     * Method to evict the SKU from Redis cache.
     * @param sku String
     */
    public void evictCache(String sku) {
        cacheService.evict(sku);
    }
}