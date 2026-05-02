package com.company.inventory.controller;

import com.company.inventory.model.response.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated liveliness/readiness endpoint.
 */
@RestController
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Method to check the health of the app and DB condition
     * @return ResponseEntity<HealthResponse>
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return ResponseEntity.ok(HealthResponse.up());
        } catch (Exception ex) {
            log.warn("Health check: database unreachable", ex);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.down());
        }
    }
}
