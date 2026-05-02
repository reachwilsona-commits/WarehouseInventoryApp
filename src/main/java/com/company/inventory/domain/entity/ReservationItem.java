package com.company.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;

/**
 * Entity mapped to reservation_items table
 */
@Entity
@Table(name = "reservation_items")
public class ReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ReservationItem() {}

    public ReservationItem(Reservation reservation, String sku, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        this.reservation = reservation;
        this.sku = sku;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public String getSku() { return sku; }
    public int getQuantity() { return quantity; }
}
