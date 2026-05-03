package com.company.inventory.service;

import com.company.inventory.log.StateTransitionLogger;
import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.event.DomainEvent;
import com.company.inventory.domain.event.EventType;
import com.company.inventory.domain.factory.ReservationFactory;
import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.exception.InsufficientStockException;
import com.company.inventory.exception.InvalidStateTransitionException;
import com.company.inventory.exception.ReservationNotFoundException;
import com.company.inventory.messaging.publisher.EventPublisher;
import com.company.inventory.repository.InventoryRepository;
import com.company.inventory.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationServiceTest {

    private ReservationRepository reservationRepository;
    private InventoryRepository inventoryRepository;
    private ReservationFactory factory;
    private EventPublisher eventPublisher;
    private StateTransitionLogger transitionLogger;
    private ReservationService service;

    private final List<DomainEvent> publishedEvents = new ArrayList<>();

    @BeforeEach
    void setUp() {
        reservationRepository = mock(ReservationRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        eventPublisher = publishedEvents::add;
        transitionLogger = mock(StateTransitionLogger.class);

        ReservationProperties props = new ReservationProperties(10, "0 */2 * * * *", 100);
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        factory = new ReservationFactory(props, clock);

        service = new ReservationService(reservationRepository, inventoryRepository,
                factory, eventPublisher, transitionLogger);
        publishedEvents.clear();
    }

    @Test
    void insufficientStock_rejectsReservation() {
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());
        Inventory a = new Inventory("A100", 10);
        when(inventoryRepository.findBySkuInOrderBySkuAsc(anyCollection())).thenReturn(List.of(a));

        ReservationCommand cmd = new ReservationCommand("ORD-1",
                List.of(new ReservationCommand.Item("A100", 50)));

        assertThatThrownBy(() -> service.createReservation(cmd))
                .isInstanceOf(InsufficientStockException.class);

        // Stock unchanged
        assertThat(a.getAvailableStock()).isEqualTo(10);
        verify(reservationRepository, never()).saveAndFlush(any());
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void partialStock_rejectsEntireReservation_andDoesNotDecrementAvailableSku() {
        // SKU A100 has plenty; SKU B200 is short. Whole request must fail and A100 must not move.
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());
        Inventory a = new Inventory("A100", 100);
        Inventory b = new Inventory("B200", 1);
        when(inventoryRepository.findBySkuInOrderBySkuAsc(anyCollection())).thenReturn(List.of(a, b));

        ReservationCommand cmd = new ReservationCommand("ORD-1", List.of(
                new ReservationCommand.Item("A100", 5),
                new ReservationCommand.Item("B200", 3)));

        assertThatThrownBy(() -> service.createReservation(cmd))
                .isInstanceOf(InsufficientStockException.class);

        assertThat(a.getAvailableStock()).isEqualTo(100);
        assertThat(a.getReservedStock()).isEqualTo(0);
        assertThat(b.getAvailableStock()).isEqualTo(1);
        verify(reservationRepository, never()).saveAndFlush(any());
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void successfulReservation_decrementsStock_andEmitsCreatedEvent() {
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.empty());
        Inventory a = new Inventory("A100", 100);
        when(inventoryRepository.findBySkuInOrderBySkuAsc(anyCollection())).thenReturn(List.of(a));
        when(reservationRepository.saveAndFlush(any(Reservation.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ReservationCommand cmd = new ReservationCommand("ORD-1",
                List.of(new ReservationCommand.Item("A100", 30)));

        ReservationResult result = service.createReservation(cmd);

        assertThat(result.duplicate()).isFalse();
        assertThat(a.getAvailableStock()).isEqualTo(70);
        assertThat(a.getReservedStock()).isEqualTo(30);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).type()).isEqualTo(EventType.RESERVATION_CREATED);
    }

    @Test
    void duplicateOrderId_returnsExistingReservation_withoutDecrementing() {
        Inventory a = new Inventory("A100", 100);
        Reservation existing = new Reservation(UUID.randomUUID(), "ORD-1",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(10));
        when(reservationRepository.findByOrderId("ORD-1")).thenReturn(Optional.of(existing));

        ReservationCommand cmd = new ReservationCommand("ORD-1",
                List.of(new ReservationCommand.Item("A100", 30)));

        ReservationResult result = service.createReservation(cmd);

        assertThat(result.duplicate()).isTrue();
        assertThat(result.reservation()).isSameAs(existing);
        assertThat(a.getAvailableStock()).isEqualTo(100);
        verify(reservationRepository, never()).saveAndFlush(any());
        verify(inventoryRepository, never()).findBySkuInOrderBySkuAsc(any());
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    void confirm_movesPendingToConfirmed_andEmitsConfirmedEvent() {
        UUID id = UUID.randomUUID();
        Reservation r = new Reservation(id, "ORD-1",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(10));
        when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));

        Reservation confirmed = service.confirmReservation(id);

        assertThat(confirmed.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).type()).isEqualTo(EventType.RESERVATION_CONFIRMED);
    }

    @Test
    void confirm_throwsWhenReservationNotFound() {
        UUID id = UUID.randomUUID();
        when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.confirmReservation(id))
                .isInstanceOf(ReservationNotFoundException.class);
    }

    @Test
    void confirm_throwsForCancelledReservation_viaStatePattern() {
        UUID id = UUID.randomUUID();
        Reservation r = new Reservation(id, "ORD-1",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(10));
        r.cancel();
        when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.confirmReservation(id))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void cancel_releasesStock_andEmitsCancelledEvent() {
        UUID id = UUID.randomUUID();
        Reservation r = new Reservation(id, "ORD-1",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(10));
        r.addItem("A100", 30);
        when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));

        Inventory a = new Inventory("A100", 100);
        a.reserve(30);
        when(inventoryRepository.findBySkuInOrderBySkuAsc(anyCollection())).thenReturn(List.of(a));

        Reservation cancelled = service.cancelReservation(id);

        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(a.getAvailableStock()).isEqualTo(100);
        assertThat(a.getReservedStock()).isEqualTo(0);
        assertThat(publishedEvents).hasSize(1);
        assertThat(publishedEvents.get(0).type()).isEqualTo(EventType.RESERVATION_CANCELLED);
    }

    @Test
    void cancel_throwsForConfirmedReservation_viaStatePattern() {
        UUID id = UUID.randomUUID();
        Reservation r = new Reservation(id, "ORD-1",
                java.time.OffsetDateTime.now(), java.time.OffsetDateTime.now().plusMinutes(10));
        r.confirm();
        when(reservationRepository.findByIdForUpdate(id)).thenReturn(Optional.of(r));
        assertThatThrownBy(() -> service.cancelReservation(id))
                .isInstanceOf(InvalidStateTransitionException.class);
    }
}
