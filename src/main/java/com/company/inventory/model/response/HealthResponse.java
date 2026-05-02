package com.company.inventory.model.response;

import java.time.OffsetDateTime;

/**
 * Record class for health api response
 * @param status String
 * @param database String
 * @param timestamp OffsetDateTime
 */
public record HealthResponse(
        String status,
        String database,
        OffsetDateTime timestamp
) {
    public static HealthResponse up()   { return new HealthResponse("UP",   "UP",   OffsetDateTime.now()); }
    public static HealthResponse down() { return new HealthResponse("DOWN", "DOWN", OffsetDateTime.now()); }
}
