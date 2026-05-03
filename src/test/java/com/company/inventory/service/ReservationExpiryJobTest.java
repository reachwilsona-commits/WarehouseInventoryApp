package com.company.inventory.service;

import com.company.inventory.log.StateTransitionLogger;
import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.domain.event.EventType;
import com.company.inventory.domain.event.ReservationCancelledEvent;
import com.company.inventory.domain.factory.ReservationFactory;
import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.messaging.publisher.EventPublisher;
import com.company.inventory.repository.InventoryRepository;
import com.company.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservationExpiryJobTest {

    private ReservationRepository reservationRepository;
    private InventoryRepository inventoryRepository;
    private ReservationService service;
    private final List<DomainEvent> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        EventPublisher publisher = events::add;
        StateTransitionLogger logger = mock(StateTransitionLogger.class);

        ReservationProperties props = new ReservationProperties(10, "0 */2 * * * *", 100);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        ReservationFactory factory = new ReservationFactory(props, clock);

        service = new ReservationService(reservationRepository, inventoryRepository, factory, publisher, logger);
        events.clear();
    }

    @Test
    void expiredPendingReservation_isCancelled_andStockReturned_withTtlExpiredReason() {
        UUID id = UUID.randomUUID();
        Reservation expired = new Reservation(id, "ORD-1",
                OffsetDateTime.now().minusMinutes(20),
                OffsetDateTime.now().minusMinutes(10));
        expired.addItem("A100", 5);

        Inventory a = new Inventory("A100", 100);
        a.reserve(5);

        when(reservationRepository.findExpired(any(OffsetDateTime.class), anyInt()))
                .thenReturn(new ArrayList<>(List.of(expired)));
        when(inventoryRepository.findBySkuInOrderBySkuAsc(anyCollection()))
                .thenReturn(List.of(a));

        List<Reservation> processed = service.findAndExpireReservation(100);

        assertThat(processed).hasSize(1);
        assertThat(expired.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(a.getAvailableStock()).isEqualTo(100);
        assertThat(a.getReservedStock()).isEqualTo(0);

        assertThat(events).hasSize(1);
        DomainEvent event = events.get(0);
        assertThat(event.type()).isEqualTo(EventType.RESERVATION_CANCELLED);
        assertThat(((ReservationCancelledEvent) event).reason().name()).isEqualTo("TTL_EXPIRED");
    }

    @Test
    void recentReservation_notReturnedByClaim_isNotTouched() {
        // The skip-locked claim query returns nothing for non-expired rows.
        when(reservationRepository.findExpired(any(OffsetDateTime.class), anyInt()))
                .thenReturn(new ArrayList<>());
        List<Reservation> processed = service.findAndExpireReservation(100);
        assertThat(processed).isEmpty();
        assertThat(events).isEmpty();
    }
}
