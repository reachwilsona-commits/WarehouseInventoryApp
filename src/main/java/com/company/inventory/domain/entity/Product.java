package com.company.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Entity mapped to product table
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected Product() {}

    public Product(String sku, String name, String description) {
        this.sku = sku;
        this.name = name;
        this.description = description;
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
