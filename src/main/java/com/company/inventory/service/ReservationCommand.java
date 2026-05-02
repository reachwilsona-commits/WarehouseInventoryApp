package com.company.inventory.service;

import java.util.List;

/**
 * Service-layer input shape for creating a reservation.
 * Decoupled from the API DTO so the service has no compile-time dependency on the web layer.
 */
public record ReservationCommand(String orderId, List<Item> items) {
    public record Item(String sku, int quantity) {}
}
