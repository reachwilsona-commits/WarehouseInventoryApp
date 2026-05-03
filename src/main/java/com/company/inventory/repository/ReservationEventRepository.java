package com.company.inventory.repository;

import com.company.inventory.domain.entity.ReservationEvents;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Reservation Event for NATS
 */
public interface ReservationEventRepository extends JpaRepository<ReservationEvents, Long> {
    List<ReservationEvents> findByReservationIdOrderByIdAsc(UUID reservationId);

    /**
     * Returns unpublished outbox rows in insertion order, limited by the supplied Pageable.
     * */
    @Query("SELECT e FROM ReservationEvents e WHERE e.publishedAt IS NULL ORDER BY e.id ASC")
    List<ReservationEvents> findUnpublishedOrderByIdAsc(Pageable pageable);
}
