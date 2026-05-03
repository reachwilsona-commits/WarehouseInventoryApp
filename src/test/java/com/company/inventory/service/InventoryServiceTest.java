package com.company.inventory.service;

import com.company.inventory.cache.InventoryCacheService;
import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.exception.SkuNotFoundException;
import com.company.inventory.model.response.InventoryResponse;
import com.company.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec coverage for InventoryService:
 *  - getBySku returns inventory entity from DB when SKU exists
 *  - getBySku throws SkuNotFoundException when SKU is absent
 *  - getCachedBySku returns cached InventoryResponse on cache hit (no DB call)
 *  - getCachedBySku falls through to DB on cache miss and populates cache
 *  - getCachedBySku throws SkuNotFoundException when absent in both cache and DB
 *  - evictCache delegates to the cache service
 */
class InventoryServiceTest {

    private InventoryRepository repository;
    private InventoryCacheService cacheService;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository   = mock(InventoryRepository.class);
        cacheService = mock(InventoryCacheService.class);
        service = new InventoryService(repository, cacheService);
    }

    @Test
    void getBySku_returnsInventory_whenSkuExists() {
        Inventory inv = new Inventory("A100", 50);
        when(repository.findById("A100")).thenReturn(Optional.of(inv));

        assertThat(service.getBySku("A100")).isSameAs(inv);
    }

    @Test
    void getBySku_throwsSkuNotFoundException_whenSkuAbsent() {
        when(repository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySku("MISSING"))
                .isInstanceOf(SkuNotFoundException.class);
    }

    @Test
    void getCachedBySku_returnsCachedResponse_withoutHittingDb() {
        InventoryResponse cached = new InventoryResponse("A100", 100, 80, 20);
        when(cacheService.get("A100")).thenReturn(Optional.of(cached));

        InventoryResponse result = service.getInventoryBySku("A100");

        assertThat(result).isSameAs(cached);
        verify(repository, never()).findById("A100");
    }

    @Test
    void getCachedBySku_loadsFromDb_andPopulatesCache_onCacheMiss() {
        when(cacheService.get("A100")).thenReturn(Optional.empty());
        Inventory inv = new Inventory("A100", 100);
        inv.reserve(20);
        when(repository.findById("A100")).thenReturn(Optional.of(inv));

        InventoryResponse result = service.getInventoryBySku("A100");

        assertThat(result.sku()).isEqualTo("A100");
        assertThat(result.availableStock()).isEqualTo(80);
        verify(cacheService).put("A100", result);
    }

    @Test
    void getCachedBySku_throwsSkuNotFoundException_whenAbsentInCacheAndDb() {
        when(cacheService.get("MISSING")).thenReturn(Optional.empty());
        when(repository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInventoryBySku("MISSING"))
                .isInstanceOf(SkuNotFoundException.class);
    }

    @Test
    void evictCache_delegatesToCacheService() {
        service.evictCache("A100");

        verify(cacheService).evict("A100");
    }
}