package com.company.inventory.controller;

import com.company.inventory.domain.event.CancellationReason;
import com.company.inventory.exception.IdempotentRetryException;
import com.company.inventory.log.StateTransitionLogger;
import com.company.inventory.model.response.ApiResponse;
import com.company.inventory.model.request.CreateReservationRequest;
import com.company.inventory.model.response.PageResponse;
import com.company.inventory.model.response.ReservationResponse;
import com.company.inventory.domain.entity.Reservation;
import com.company.inventory.domain.entity.ReservationStatus;
import com.company.inventory.exception.ErrorCode;
import com.company.inventory.service.ReservationCommand;
import com.company.inventory.service.ReservationResult;
import com.company.inventory.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller class for Reservation REST endpoints
 */
@RestController
@RequestMapping("/api/v1/reservations")
@Validated
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    /**
     * End point to create a Reservation
     * @param request CreateReservationRequest
     * @return ReservationResponse
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ReservationResponse>> createReservation(
            @Valid @RequestBody CreateReservationRequest request) {

        ReservationResult result;
        try {
            result = service.createReservation(toCommand(request));
        } catch (IdempotentRetryException race) {
            //A concurrent request inserted the same orderId first, so fetch it again and treat it as a duplicate.
            Reservation existing = service.fetchExistingByOrderId(race.orderId());
            return duplicate(existing);
        }

        if (result.duplicate()) {
            return duplicate(result.reservation());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ReservationResponse.from(result.reservation())));
    }

    /**
     * Return HTTP 200 with a DUPLICATE_ORDER flag, so the client gets the existing reservation,
     * and include a header to track duplicates without reading the response body.
     * @param existingReservation Reservation
     * @return ReservationResponse
     */
    private ResponseEntity<ApiResponse<ReservationResponse>> duplicate(Reservation existingReservation) {
        return ResponseEntity.status(HttpStatus.OK)
                .header("X-Idempotent-Replay", "true")
                .header("X-Error-Code", ErrorCode.DUPLICATE_ORDER.name())
                .body(ApiResponse.success(ReservationResponse.from(existingReservation)));
    }

    /**
     * End point to confirm a Reservation with id
     * @param id UUID
     * @return ReservationResponse
     */
    @PostMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<ReservationResponse>> confirmReservation(@PathVariable UUID id) {
        Reservation r = service.confirmReservation(id);
        return ResponseEntity.ok(ApiResponse.success(ReservationResponse.from(r)));
    }

    /**
     * End point to cancel a Reservation with id
     * @param id UUID
     * @return ReservationResponse
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ReservationResponse>> cancelReservation(@PathVariable UUID id) {
        var reservation= service.cancelReservation(id);
        return ResponseEntity.ok(ApiResponse.success(ReservationResponse.from(reservation)));
    }

    /**
     * End point to get a Reservation by id
     * @param id UUID
     * @return ReservationResponse
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ReservationResponse>> getReservation(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ReservationResponse.from(service.get(id))));
    }

    /**
     * End point to get list of reservation with pagination
     * @param page int
     * @param size int
     * @param status ReservationStatus
     * @return ReservationResponse
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ReservationResponse>>> list(
            @NotNull(message = "page is required")
            @Min(value = 0, message = "page must be >= 0")
            @RequestParam(name = "page") Integer page,
            @NotNull(message = "size is required")
            @Min(value = 1, message = "size must be >= 1")
            @RequestParam(name = "size") Integer size,
            @RequestParam(name = "status", required = false) ReservationStatus status) {

        Page<ReservationResponse> result = service.list(page, size, status);
        return ResponseEntity.ok(ApiResponse.success(
                PageResponse.of(result, java.util.function.Function.identity())));
    }

    private static ReservationCommand toCommand(CreateReservationRequest req) {
        return new ReservationCommand(
                req.orderId(),
                req.items().stream()
                        .map(i -> new ReservationCommand.Item(i.sku(), i.quantity()))
                        .toList());
    }
}
