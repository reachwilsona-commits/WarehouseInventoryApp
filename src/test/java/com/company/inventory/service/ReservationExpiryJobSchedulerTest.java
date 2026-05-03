package com.company.inventory.service;

import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.entity.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class ReservationExpiryJobSchedulerTest {

    private ReservationService service;
    private ReservationExpiryJob job;

    @BeforeEach
    void setUp() {
        service = mock(ReservationService.class);
        ReservationProperties props = new ReservationProperties(10, "0 */2 * * * *", 5);
        job = new ReservationExpiryJob(service, props);
    }

    @Test
    void run_doesNothing_whenOutboxIsEmpty() {
        when(service.findAndExpireReservation(5)).thenReturn(Collections.emptyList());

        job.run();

        verify(service, times(1)).findAndExpireReservation(5);
    }

    @Test
    void run_processesPartialBatch_andExits() {
        List<Reservation> partial = List.of(reservation(), reservation());
        when(service.findAndExpireReservation(5)).thenReturn(partial);

        job.run();

        verify(service, times(1)).findAndExpireReservation(5);
    }

    @Test
    void run_processesMultipleBatches_untilPartialSignalsEnd() {
        List<Reservation> fullBatch    = List.of(reservation(), reservation(), reservation(), reservation(), reservation());
        List<Reservation> partialBatch = List.of(reservation());

        when(service.findAndExpireReservation(5))
                .thenReturn(fullBatch)
                .thenReturn(partialBatch);

        job.run();

        verify(service, times(2)).findAndExpireReservation(5);
    }

    private Reservation reservation() {
        return new Reservation(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(10));
    }
}
