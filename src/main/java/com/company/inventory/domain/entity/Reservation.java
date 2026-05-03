package com.company.inventory.domain.entity;

import com.company.inventory.domain.state.ReservationState;
import com.company.inventory.domain.state.ReservationStateFactory;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Entity mapped to reservations table
 */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true, length = 128)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true,
               fetch = FetchType.EAGER)
    private List<ReservationItem> items = new ArrayList<>();

    protected Reservation() {}

    public Reservation(UUID id, String orderId, OffsetDateTime createdAt, OffsetDateTime expiresAt) {
        this.id = id;
        this.orderId = orderId;
        this.status = ReservationStatus.PENDING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Method to add item to the reservation
     * @param sku String
     * @param quantity int
     */
    public void addItem(String sku, int quantity) {
        items.add(new ReservationItem(this, sku, quantity));
    }

    /**
     * Method to confirm the apply a state transition for confirm
     */
    public void confirm() {
        ReservationState current = ReservationStateFactory.forStatus(status);
        current.confirm(this);
        this.updatedAt = OffsetDateTime.now();
    }
    /**
     * Method to confirm the apply a state transition for cancel
     */
    public void cancel() {
        ReservationState current = ReservationStateFactory.forStatus(status);
        current.cancel(this);
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * Method to change the status
     */
    public void changeStatusTo(ReservationStatus newStatus) {
        this.status = newStatus;
    }

    @Transient
    public boolean isExpired() {
        return status == ReservationStatus.PENDING && OffsetDateTime.now().isAfter(expiresAt);
    }

    public UUID getId() { return id; }
    public String getOrderId() { return orderId; }
    public ReservationStatus getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }
    public List<ReservationItem> getItems() { return Collections.unmodifiableList(items); }
}
