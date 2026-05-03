package com.company.inventory.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity mapped to reservation_events table
 */
@Entity
@Table(name = "reservation_events")
public class ReservationEvents {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    protected ReservationEvents() {}

    public ReservationEvents(UUID reservationId, String eventType, String payloadJson) {
        this.reservationId = reservationId;
        this.eventType = eventType;
        this.payload = payloadJson;
        this.createdAt = OffsetDateTime.now();
    }

    public void markPublished() {
        this.publishedAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public UUID getReservationId() { return reservationId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }

}
