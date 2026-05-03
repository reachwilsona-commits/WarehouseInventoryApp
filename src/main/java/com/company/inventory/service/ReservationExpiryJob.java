package com.company.inventory.service;

import com.company.inventory.cache.DistributedLockService;
import com.company.inventory.config.RedisProperties;
import com.company.inventory.config.ReservationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Scheduler job cancels expired PENDING reservations and returns their stock.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService reservationService;
    private final ReservationProperties properties;
    private final DistributedLockService lockService;
    private final RedisProperties redisProperties;

    public ReservationExpiryJob(ReservationService reservationService,
                                 ReservationProperties properties,
                                 DistributedLockService lockService,
                                 RedisProperties redisProperties) {
        this.reservationService = reservationService;
        this.properties = properties;
        this.lockService = lockService;
        this.redisProperties = redisProperties;
    }

    @Scheduled(cron = "${app.reservation.expiry-job-cron}")
    public void run() {
        String lockKey = redisProperties.expiryJobLockKey();
        Duration lockTtl = Duration.ofSeconds(redisProperties.expiryJobLockTtlSeconds());

        if (!lockService.tryAcquire(lockKey, lockTtl)) {
            log.debug("Expiry job skipped Lock key={} held by another instance.", lockKey);
            return;
        }

        try {
            long start = System.currentTimeMillis();
            int total = 0;
            while (true) {
                List<?> batch = reservationService.findAndExpireReservation(properties.expiryBatchSize());
                total += batch.size();
                if (batch.size() < properties.expiryBatchSize()) break;
            }
            if (total > 0) {
                log.info("Expiry job  completed={} Time took={}", total, System.currentTimeMillis() - start);
            }
        } finally {
            lockService.release(lockKey);
        }
    }
}