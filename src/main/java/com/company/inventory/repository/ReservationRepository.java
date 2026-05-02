package com.company.inventory.repository;

import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Class for Reservation Repository
 */
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Reservation> findByOrderId(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "items")
    @Override
    Optional<Reservation> findById(UUID id);

    Page<Reservation> findAll(Pageable pageable);

    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    /**
     * Method to find expired reservation used by Expiry-job
     */
    @Query(value = """
        select * from reservations
         where status = 'PENDING' and expires_at < :now
         order by expires_at asc
         limit :limit
         for update skip locked
        """, nativeQuery = true)
    List<Reservation> findExpired(@Param("now") OffsetDateTime now, @Param("limit") int limit);

}
