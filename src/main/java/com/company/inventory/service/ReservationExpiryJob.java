package com.company.inventory.service;

import com.company.inventory.config.ReservationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduled job that cancels expired PENDING reservations and returns their stock.
 */
@Component
public class ReservationExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReservationExpiryJob.class);

    private final ReservationService reservationService;
    private final ReservationProperties properties;

    public ReservationExpiryJob(ReservationService reservationService, ReservationProperties properties) {
        this.reservationService = reservationService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.reservation.expiry-job-cron}")
    public void run() {
        long start = System.currentTimeMillis();
        int total = 0;
        while (true) {
            List<?> batch = reservationService.findAndExpireReservation(properties.expiryBatchSize());
            total += batch.size();
            if (batch.size() < properties.expiryBatchSize()) break;
        }
        if (total > 0) {
            log.info("expiry_job_complete cancelled={} elapsed_ms={}", total, System.currentTimeMillis() - start);
        }
    }
}
