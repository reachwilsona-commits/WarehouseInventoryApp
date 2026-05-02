package com.company.inventory.service;

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
 * Service class for Reservation
 */
@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryRepository inventoryRepository;
    private final ReservationFactory reservationFactory;
    private final EventPublisher eventPublisher;
    private final StateTransitionLogger transitionLogger;

    public ReservationService(ReservationRepository reservationRepository,
                              InventoryRepository inventoryRepository,
                              ReservationFactory reservationFactory,
                              EventPublisher eventPublisher,
                              StateTransitionLogger transitionLogger) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.reservationFactory = reservationFactory;
        this.eventPublisher = eventPublisher;
        this.transitionLogger = transitionLogger;
    }

    /**
     * Method to create a new reservation, atomically deducting stock and persisting an event.
     */
    @Transactional
    public ReservationResult createReservation(ReservationCommand command) {
        // 1. Check for existence of orderId
        var order = reservationRepository.findByOrderId(command.orderId());
        if (order.isPresent()) {
            return ReservationResult.duplicate(order.get());
        }

        // 2. Lock the inventory rows for reservation as sorted List
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
                .ifPresent(sku -> {
                    throw new SkuNotFoundException(sku);
                });

        //3. Deduplicate the requested SKU
        Map<String, Integer> requestedItem =
                command.items().stream()
                        .collect(Collectors.toMap(
                                ReservationCommand.Item::sku,
                                ReservationCommand.Item::quantity,
                                Integer::sum
                        ));

        //4. Make sure requested SKU's are available
        requestedItem.entrySet().stream()
                .filter(e -> bySku.get(e.getKey()).getAvailableStock() < e.getValue())
                .findFirst()
                .ifPresent(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    throw new InsufficientStockException(
                            e.getKey(),
                            inv.getAvailableStock(),
                            e.getValue()
                    );
                });

        //5. Apply deductions from Inventory
        requestedItem.entrySet().stream()
                .filter(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    return !inv.reserve(e.getValue());
                })
                .findFirst()
                .ifPresent(e -> {
                    Inventory inv = bySku.get(e.getKey());
                    throw new InsufficientStockException(
                            e.getKey(),
                            inv.getAvailableStock(),
                            e.getValue()
                    );
                });
        //6. Save the reservation
        var reservation = saveReservation(command);
        // 7. publish the reservation as Event
        publishEvent(EventType.RESERVATION_CREATED, reservation, null);
        //8. log the transition
        logTransition(reservation, null, ReservationStatus.PENDING);
        //9. Return the reservation response
        return ReservationResult.created(reservation);
    }
    /**
     * Method to save the reservation
     * @param command ReservationCommand
     */
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

    /**
     * Method to find Existing  Order By OrderId
     * @param orderId String
     * @return Reservation
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public Reservation fetchExistingByOrderId(String orderId) {
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotent retry path: expected reservation for orderId " + orderId + " not found"));
    }

    /**
     * Method to confirm the reservation
     * @param id UUID
     * @return Reservation
     */
    @Transactional
    public Reservation confirmReservation(UUID id) {
        // 1. Find the reservation
        var reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        // 2. Confirm the reservation
        reservation.confirm();
        //3. Publish the event
        publishEvent(EventType.RESERVATION_CONFIRMED, reservation, null);
        //4. Log the event
        logTransition(reservation, reservation.getStatus(), reservation.getStatus());
        return reservation;
    }

    /**
     * Method to Cancel the Reservation from the API call
     * @param id UUID
     * @return Reservation
     */
    @Transactional
    public Reservation cancelReservation(UUID id) {
        //1. Find the reservation by Id
        var reservation = reservationRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
        reservation = cancelInternal(reservation, CancellationReason.USER_REQUEST, StateTransitionLogger.Trigger.API);
        return reservation;
    }

    /**
     * Common method to cancel the reservation from both API and Scheduler Job
     * @param reservation Reservation
     * @param reason CancellationReason
     * @param trigger Trigger
     * @return Reservation
     */
    @Transactional
    public Reservation cancelInternal(Reservation reservation, CancellationReason reason, Trigger trigger ) {
        //1. Cancel the reservation, throws exception if not PENDING.
        reservation.cancel();
        // 2. Sort SKU's from the Reservation
        List<String> skus = reservation.getItems().stream()
                .map(ReservationItem::getSku)
                .sorted()
                .toList();
        // 3. Find the Inventory items from SKU's
        var lockedInventory = inventoryRepository.findBySkuInOrderBySkuAsc(skus);
        Map<String, Inventory> skuMap =
                lockedInventory.stream()
                        .collect(Collectors.toMap(
                                Inventory::getSku,
                                Function.identity()
                        ));
        //4. Group items by SKU in case the same SKU appears multiple times.
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
                .ifPresent(e -> {
                    throw new SkuNotFoundException(e.getKey());
                });

        qtySkuMap.forEach((sku, qty) ->
                skuMap.get(sku).release(qty)
        );
        //5. Publish Event
        publishEvent(EventType.RESERVATION_CANCELLED, reservation, reason);
        //6. Log the Event
        logTransition(reservation, reservation.getStatus(), reservation.getStatus());
        return reservation;
    }

    /**
     * Fetch Reservation by UUID
     * @param id UUID
     * @return Reservation
     */
    @Transactional(readOnly = true)
    public Reservation get(UUID id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new ReservationNotFoundException(id));
    }

    /**
     * Get Pageable ReservationResponse
     * @param page int
     * @param size int
     * @param status ReservationStatus
     * @return Page<ReservationResponse>
     */
    @Transactional(readOnly = true)
    public Page<ReservationResponse> list(int page, int size, ReservationStatus status) {
        if (page < 0)            throw new IllegalArgumentException("page must be >= 0");
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

    /**
     * This method is triggered by Scheduled Job
     * @param limit int
     * @return List<Reservation>
     */
    @Transactional
    public List<Reservation> findAndExpireReservation(int limit) {
        List<Reservation> expiredReservation = reservationRepository.findExpired(OffsetDateTime.now(), limit);
        expiredReservation.sort(Comparator.comparing(Reservation::getId));
        for (Reservation reservation : expiredReservation) {
            cancelInternal(reservation, CancellationReason.TTL_EXPIRED, Trigger.EXPIRY_JOB);
        }
        return expiredReservation;
    }

    /**
     * Common method to handle all type of Events
     * @param eventType EventType
     * @param reservation Reservation
     */
    private void publishEvent(EventType eventType,
                              Reservation reservation,
                              CancellationReason cancellationReason) {
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
                    reservation.getId(),
                    reservation.getOrderId(),
                    OffsetDateTime.now());

            case RESERVATION_CANCELLED -> new ReservationCancelledEvent(
                    reservation.getId(),
                    reservation.getOrderId(),
                    OffsetDateTime.now(),
                    cancellationReason);
        };
        eventPublisher.publish(event);
    }

    /**
     * Method to LOG the transition
     * @param reservation Reservation
     * @param from        ReservationStatus
     * @param to          ReservationStatus
     */
    private void logTransition(Reservation reservation, ReservationStatus from, ReservationStatus to) {
        transitionLogger.logTransition(
                reservation.getId(), reservation.getOrderId(),
                from, to, Trigger.API);
    }
}
