package com.company.inventory.service;

import com.company.inventory.cache.DistributedLockService;
import com.company.inventory.cache.NoOpDistributedLockService;
import com.company.inventory.config.RedisProperties;
import com.company.inventory.config.ReservationProperties;
import com.company.inventory.domain.entity.Reservation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec coverage for ReservationExpiryJob scheduler logic:
 *  - run() drains empty outbox in one call
 *  - run() stops after a partial batch
 *  - run() loops until a partial batch signals end
 *  - run() skips processing when the distributed lock is already held
 *  - run() releases the lock after processing completes
 */
class ReservationExpiryJobSchedulerTest {

    private ReservationService service;
    private ReservationExpiryJob job;

    private static final ReservationProperties PROPS =
            new ReservationProperties(10, "0 */2 * * * *", 5);
    private static final RedisProperties REDIS_PROPS =
            new RedisProperties("localhost", 6379, false, 30L, "lock:expiry-job", 90L);

    @BeforeEach
    void setUp() {
        service = mock(ReservationService.class);
        job = new ReservationExpiryJob(service, PROPS, new NoOpDistributedLockService(), REDIS_PROPS);
    }

    @Test
    void run_doesNothing_whenOutboxIsEmpty() {
        when(service.findAndExpireReservation(5)).thenReturn(Collections.emptyList());

        job.run();

        verify(service, times(1)).findAndExpireReservation(5);
    }

    @Test
    void run_processesPartialBatch_andExits() {
        when(service.findAndExpireReservation(5)).thenReturn(List.of(reservation(), reservation()));

        job.run();

        verify(service, times(1)).findAndExpireReservation(5);
    }

    @Test
    void run_processesMultipleBatches_untilPartialSignalsEnd() {
        List<Reservation> full    = List.of(reservation(), reservation(), reservation(), reservation(), reservation());
        List<Reservation> partial = List.of(reservation());

        when(service.findAndExpireReservation(5)).thenReturn(full).thenReturn(partial);

        job.run();

        verify(service, times(2)).findAndExpireReservation(5);
    }

    @Test
    void run_skipsProcessing_whenLockAlreadyHeld() {
        DistributedLockService lock = mock(DistributedLockService.class);
        when(lock.tryAcquire(eq("lock:expiry-job"), any(Duration.class))).thenReturn(false);

        ReservationExpiryJob lockedJob =
                new ReservationExpiryJob(service, PROPS, lock, REDIS_PROPS);
        lockedJob.run();

        verify(service, never()).findAndExpireReservation(any(Integer.class));
    }

    @Test
    void run_releasesLock_afterProcessing() {
        DistributedLockService lock = mock(DistributedLockService.class);
        when(lock.tryAcquire(eq("lock:expiry-job"), any(Duration.class))).thenReturn(true);
        when(service.findAndExpireReservation(5)).thenReturn(Collections.emptyList());

        ReservationExpiryJob lockedJob =
                new ReservationExpiryJob(service, PROPS, lock, REDIS_PROPS);
        lockedJob.run();

        verify(lock).release("lock:expiry-job");
    }

    private Reservation reservation() {
        return new Reservation(UUID.randomUUID(), "ORD-" + UUID.randomUUID(),
                OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(10));
    }
}