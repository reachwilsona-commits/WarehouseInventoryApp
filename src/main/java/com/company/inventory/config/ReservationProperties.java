package com.company.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Property class for Reservation
 * @param ttlMinutes long
 * @param expiryJobCron String
 * @param expiryBatchSize int
 */
@ConfigurationProperties(prefix = "app.reservation")
public record ReservationProperties(
        long ttlMinutes,
        String expiryJobCron,
        int expiryBatchSize
) {
    public ReservationProperties {
        if (ttlMinutes <= 0) throw new IllegalArgumentException("ttlMinutes must be > 0");
        if (expiryBatchSize <= 0) throw new IllegalArgumentException("expiryBatchSize must be > 0");
    }
}
