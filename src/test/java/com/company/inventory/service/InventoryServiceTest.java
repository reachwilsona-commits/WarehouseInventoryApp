package com.company.inventory.service;

import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.exception.SkuNotFoundException;
import com.company.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryServiceTest {

    private InventoryRepository repository;
    private InventoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(InventoryRepository.class);
        service = new InventoryService(repository);
    }

    @Test
    void getBySku_returnsInventory_whenSkuExists() {
        Inventory inv = new Inventory("A100", 50);
        when(repository.findById("A100")).thenReturn(Optional.of(inv));

        Inventory result = service.getBySku("A100");

        assertThat(result).isSameAs(inv);
    }

    @Test
    void getBySku_throwsSkuNotFoundException_whenSkuAbsent() {
        when(repository.findById("MISSING")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBySku("MISSING"))
                .isInstanceOf(SkuNotFoundException.class);
    }
}