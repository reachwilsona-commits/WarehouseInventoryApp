package com.company.inventory.model.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Record class for Create Reservation Request
 * @param orderId String
 * @param items List<Item>
 */
public record CreateReservationRequest(
        @NotBlank @Size(max = 128) String orderId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @Positive int quantity
    ) {}
}
