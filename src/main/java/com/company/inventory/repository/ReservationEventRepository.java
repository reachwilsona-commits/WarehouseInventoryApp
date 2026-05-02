package com.company.inventory.repository;

import com.company.inventory.domain.entity.ReservationEvents;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Class for Reservation Event Repository
 */
public interface ReservationEventRepository extends JpaRepository<ReservationEvents, Long> {
    List<ReservationEvents> findByReservationIdOrderByIdAsc(UUID reservationId);
}
