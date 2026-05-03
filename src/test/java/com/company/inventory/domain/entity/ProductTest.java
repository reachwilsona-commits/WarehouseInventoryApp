package com.company.inventory.domain.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    void constructor_setsAllFields() {
        Product p = new Product("SKU-001", "Widget", "A small widget");

        assertThat(p.getSku()).isEqualTo("SKU-001");
        assertThat(p.getName()).isEqualTo("Widget");
        assertThat(p.getDescription()).isEqualTo("A small widget");
    }

    @Test
    void constructor_allowsNullDescription() {
        Product p = new Product("SKU-002", "Gadget", null);

        assertThat(p.getSku()).isEqualTo("SKU-002");
        assertThat(p.getName()).isEqualTo("Gadget");
        assertThat(p.getDescription()).isNull();
    }
}