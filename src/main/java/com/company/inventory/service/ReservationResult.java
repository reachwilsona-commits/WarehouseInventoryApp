package com.company.inventory.service;

import com.company.inventory.domain.entity.Reservation;

/**
 * Wrapper that tells the controller whether a reservation was created fresh (HTTP 201)
 * or returned because of an idempotent duplicate (HTTP 200 + DUPLICATE_ORDER semantics).
 */
public record ReservationResult(Reservation reservation, boolean duplicate) {
    public static ReservationResult created(Reservation r)    { return new ReservationResult(r, false); }
    public static ReservationResult duplicate(Reservation r)  { return new ReservationResult(r, true);  }
}
