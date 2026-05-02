package com.company.inventory.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void reserveDecrementsAvailableAndIncrementsReserved() {
        Inventory inv = new Inventory("A100", 100);
        boolean ok = inv.reserve(30);
        assertThat(ok).isTrue();
        assertThat(inv.getAvailableStock()).isEqualTo(70);
        assertThat(inv.getReservedStock()).isEqualTo(30);
        assertThat(inv.getTotalStock()).isEqualTo(100);
    }

    @Test
    void reserveReturnsFalseAndDoesNotMutateWhenInsufficient() {
        Inventory inv = new Inventory("A100", 10);
        boolean ok = inv.reserve(50);
        assertThat(ok).isFalse();
        assertThat(inv.getAvailableStock()).isEqualTo(10);
        assertThat(inv.getReservedStock()).isEqualTo(0);
    }

    @Test
    void releaseRestoresStock() {
        Inventory inv = new Inventory("A100", 100);
        inv.reserve(40);
        inv.release(40);
        assertThat(inv.getAvailableStock()).isEqualTo(100);
        assertThat(inv.getReservedStock()).isEqualTo(0);
    }

    @Test
    void releaseRejectsAmountLargerThanReserved() {
        Inventory inv = new Inventory("A100", 100);
        inv.reserve(10);
        assertThatThrownBy(() -> inv.release(20)).isInstanceOf(IllegalStateException.class);
    }
}
