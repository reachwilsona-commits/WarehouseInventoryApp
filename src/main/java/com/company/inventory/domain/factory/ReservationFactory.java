package com.company.inventory.domain.factory;

import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.model.request.RequestedItem;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Factory pattern — the single, authoritative way a {@link Reservation} aggregate is built.
 */
@Component
public class ReservationFactory {

    private final ReservationProperties properties;
    private final java.time.Clock clock;

    public ReservationFactory(ReservationProperties properties, java.time.Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Reservation create(String orderId, List<RequestedItem> items) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId must be non-blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must be non-empty");
        }
        // Check duplicate SKUs in the same request — the DB has a UNIQUE (reservation_id, sku)
        Map<String, Integer> map = new java.util.LinkedHashMap<>();
        for (RequestedItem item : items) {
            if (item.sku() == null || item.sku().isBlank()) {
                throw new IllegalArgumentException("sku must be non-blank");
            }
            if (item.quantity() <= 0) {
                throw new IllegalArgumentException("quantity must be > 0 for SKU " + item.sku());
            }
            map.merge(item.sku(), item.quantity(), Integer::sum);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        OffsetDateTime expiresAt = now.plusMinutes(properties.ttlMinutes());
        Reservation reservation = new Reservation(UUID.randomUUID(), orderId, now, expiresAt);
        map.forEach(reservation::addItem);
        return reservation;
    }

}
