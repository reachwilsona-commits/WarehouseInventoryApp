package com.company.inventory.service;

import com.company.inventory.cache.InventoryCacheService;
import com.company.inventory.domain.event.*;
import com.company.inventory.exception.IdempotentRetryException;
import com.company.inventory.model.request.RequestedItem;
import com.company.inventory.model.response.ReservationResponse;
import com.company.inventory.log.StateTransitionLogger;
import com.company.inventory.log.StateTransitionLogger.Trigger;
import com.company.inventory.domain.factory.ReservationFactory;
import com.company.inventory.domain.entity.Inventory;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationItem;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.exception.InsufficientStockException;
import com.company.inventory.exception.ReservationNotFoundException;
import com.company.inventory.exception.SkuNotFoundException;
import com.company.inventory.messaging.publisher.EventPublisher;
import com.company.inventory.repository.InventoryRepository;
import com.company.inventory.repository.ReservationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service class for Create, Confirm, Cancel Reservation
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationFactory reservationFactory;
    private final EventPublisher eventPublisher;
    private final StateTransitionLogger transitionLogger;
    private final InventoryCacheService cacheService;

    public ReservationService(ReservationRepository reservationRepository,
                              InventoryRepository inventoryRepository,
                              ReservationFactory reservationFactory,
                              EventPublisher eventPublisher,
                              StateTransitionLogger transitionLogger,
                              InventoryCacheService cacheService) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationFactory = reservationFactory;
        this.eventPublisher = eventPublisher;
        this.transitionLogger = transitionLogger;
        this.cacheService = cacheService;
    }

    /**
     * Create a new reservation, atomically deducting stock and persisting an event.
     */
    @Transactional
    public ReservationResult createReservation(ReservationCommand command) {
        var order = reservationRepository.findByOrderId(command.orderId());
        if (order.isPresent()) {
            return ReservationResult.duplicate(order.get());
        }

        var sortedSkus = command.items().stream()
                .map(ReservationCommand.Item::sku)
                .distinct()
                .sorted()
                .toList();

        var lockedInventory = inventoryRepository.findBySkuInOrderBySkuAsc(sortedSkus);
        Map<String, Inventory> bySku = lockedInventory.stream()
                .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        sortedSkus.stream()
                .filter(sku -> !bySku.containsKey(sku))
                .findFirst()
                .ifPresent(sku -> { throw new SkuNotFoundException(sku); });

        Map<String, Integer> requestedItem =
                command.items().stream()
                        .collect(Collectors.toMap(
                                ReservationCommand.Item::sku,
                                ReservationCommand.Item::quantity,
                                Integer::sum
                        ));

        requestedItem.entrySet().stream()
                .filter(e -> bySku.get(e.getKey()).getAvailableStock() < e.getValue())
                .findFirst()
                .ifPresent(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    throw new InsufficientStockException(e.getKey(), inv.getAvailableStock(), e.getValue());
                });

        requestedItem.entrySet().stream()
                .filter(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    return !inv.reserve(e.getValue());
                })
                .findFirst()
                .ifPresent(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    throw new InsufficientStockException(e.getKey(), inv.getAvailableStock(), e.getValue());
                });

        // Invalidate cache for every SKU whose available stock changed
        requestedItem.keySet().forEach(cacheService::evict);

        var reservation = saveReservation(command);
        publishEvent(EventType.RESERVATION_CREATED, reservation, null);
        logTransition(reservation, null, ReservationStatus.PENDING, Trigger.API);
        return ReservationResult.created(reservation);
    }

    private Reservation saveReservation(ReservationCommand command) {
        var reservation = reservationFactory.create(
                command.orderId(),
                command.items().stream()
                        .map(i -> new RequestedItem(i.sku(), i.quantity()))
                        .toList());
        try {
            reservation = reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException duplicateKey) {
            throw new IdempotentRetryException(command.orderId(), duplicateKey);
        }
        return reservation;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Reservation fetchExistingByOrderId(String orderId) {
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotent retry path: expected reservation for orderId " + orderId + " not found"));
    }

    @Transactional
    public Reservation confirmReservation(UUID id) {
        var reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        reservation.confirm();
        publishEvent(EventType.RESERVATION_CONFIRMED, reservation, null);
        logTransition(reservation, ReservationStatus.PENDING, reservation.getStatus(), Trigger.API);
        return reservation;
    }

    @Transactional
    public Reservation cancelReservation(UUID id) {
        var reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        return cancelInternal(reservation, CancellationReason.USER_REQUEST, Trigger.API);
    }

    @Transactional
    public Reservation cancelInternal(Reservation reservation, CancellationReason reason, Trigger trigger) {
        // Capture the pre-cancel status so logTransition records the correct fromState
        ReservationStatus fromStatus = reservation.getStatus();
        reservation.cancel();

        List<String> skus = reservation.getItems().stream()
                .map(ReservationItem::getSku)
                .sorted()
                .toList();

        var lockedInventory = inventoryRepository.findBySkuInOrderBySkuAsc(skus);
        Map<String, Inventory> skuMap =
                lockedInventory.stream()
                        .collect(Collectors.toMap(Inventory::getSku, Function.identity()));

        Map<String, Integer> qtySkuMap =
                reservation.getItems().stream()
                        .collect(Collectors.toMap(
                                ReservationItem::getSku,
                                ReservationItem::getQuantity,
                                Integer::sum
                        ));

        qtySkuMap.entrySet().stream()
                .filter(e -> skuMap.get(e.getKey()) == null)
                .findFirst()
                .ifPresent(e -> { throw new SkuNotFoundException(e.getKey()); });

        qtySkuMap.forEach((sku, qty) -> skuMap.get(sku).release(qty));

        // Invalidate cache for every SKU whose available stock changed
        qtySkuMap.keySet().forEach(cacheService::evict);

        publishEvent(EventType.RESERVATION_CANCELLED, reservation, reason);
        logTransition(reservation, fromStatus, reservation.getStatus(), trigger);
        return reservation;
    }

    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> list(int page, int size, ReservationStatus status) {
        if (page < 0)                throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0 || size > 200) throw new IllegalArgumentException("size must be 1..200");

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Reservation> result = (status == null)
                ? reservationRepository.findAll(pageable)
                : reservationRepository.findByStatus(status, pageable);

        return result.map(r -> {
            r.getItems().size();
            return ReservationResponse.from(r);
        });
    }

    @Transactional
    public List<Reservation> findAndExpireReservation(int limit) {
        List<Reservation> expiredReservation = reservationRepository.findExpired(OffsetDateTime.now(), limit);
        expiredReservation.sort(Comparator.comparing(Reservation::getId));
        for (Reservation reservation : expiredReservation) {
            cancelInternal(reservation, CancellationReason.TTL_EXPIRED, Trigger.EXPIRY_JOB);
        }
        return expiredReservation;
    }

    private void publishEvent(EventType eventType, Reservation reservation, CancellationReason cancellationReason) {
        DomainEvent event = switch (eventType) {
            case RESERVATION_CREATED -> new ReservationCreatedEvent(
                    reservation.getId(),
                    reservation.getOrderId(),
                    OffsetDateTime.now(),
                    reservation.getExpiresAt(),
                    reservation.getItems().stream()
                            .map(i -> new ReservationCreatedEvent.Item(i.getSku(), i.getQuantity()))
                            .toList());
            case RESERVATION_CONFIRMED -> new ReservationConfirmedEvent(
                    reservation.getId(), reservation.getOrderId(), OffsetDateTime.now());
            case RESERVATION_CANCELLED -> new ReservationCancelledEvent(
                    reservation.getId(), reservation.getOrderId(), OffsetDateTime.now(), cancellationReason);
        };
        eventPublisher.publish(event);
    }

    private void logTransition(Reservation reservation,
                                ReservationStatus from, ReservationStatus to,
                                Trigger trigger) {
        transitionLogger.logTransition(
                reservation.getId(), reservation.getOrderId(), from, to, trigger);
    }
}