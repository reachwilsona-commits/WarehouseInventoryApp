package com.company.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Entity mapped to Inventory table
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "sku", length = 64)
    private String sku;

    @Column(name = "total_stock", nullable = false)
    private int totalStock;

    @Column(name = "available_stock", nullable = false)
    private int availableStock;

    @Column(name = "reserved_stock", nullable = false)
    private int reservedStock;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Inventory() {}

    public Inventory(String sku, int totalStock) {
        this.sku = Objects.requireNonNull(sku);
        if (totalStock < 0) throw new IllegalArgumentException("totalStock must be >= 0");
        this.totalStock = totalStock;
        this.availableStock = totalStock;
        this.reservedStock = 0;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * @return true if quantity units could be reserved or false if insufficient stock.
     */
    public boolean reserve(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (availableStock < quantity) return false;
        availableStock -= quantity;
        reservedStock  += quantity;
        updatedAt = OffsetDateTime.now();
        return true;
    }

    /**
     * Release reserved stock back to available for cancel or TTL expiry.
     **/
    public void release(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (reservedStock < quantity) {
            throw new IllegalStateException(
                    "Cannot release " + quantity + " units of SKU " + sku +
                    " — only " + reservedStock + " are reserved.");
        }
        reservedStock  -= quantity;
        availableStock += quantity;
        updatedAt = OffsetDateTime.now();
    }

    public String getSku() { return sku; }
    public int getTotalStock() { return totalStock; }
    public int getAvailableStock() { return availableStock; }
    public int getReservedStock() { return reservedStock; }
    public long getVersion() { return version; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
