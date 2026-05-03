package com.company.inventory.service;

import java.util.List;

/**
 * Command Class for service layer input shape for creating a reservation.
 */
public record ReservationCommand(String orderId, List<Item> items) {
    public record Item(String sku, int quantity) {}
}
