package com.company.inventory.model.response;

import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Class for Reservation Response
 * @param id UUID
 * @param orderId String
 * @param status ReservationStatus
 * @param createdAt OffsetDateTime
 * @param updatedAt OffsetDateTime
 * @param expiresAt OffsetDateTime
 * @param items List<Item>
 */
public record ReservationResponse(
        UUID id,
        String orderId,
        ReservationStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime expiresAt,
        List<Item> items
) {
    public record Item(String sku, int quantity) {}

    public static ReservationResponse from(Reservation r) {
        List<Item> items = r.getItems().stream()
                .map(i -> new Item(i.getSku(), i.getQuantity()))
                .toList();
        return new ReservationResponse(
                r.getId(), r.getOrderId(), r.getStatus(),
                r.getCreatedAt(), r.getUpdatedAt(), r.getExpiresAt(),
                items);
    }
}
