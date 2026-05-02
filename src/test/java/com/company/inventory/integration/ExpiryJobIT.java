package com.company.inventory.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.inventory.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiryJobIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ReservationService reservationService;

    @BeforeEach
    void resetSeed() {
        jdbc.update("DELETE FROM reservation_events");
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE inventory SET total_stock = 100, available_stock = 100, reserved_stock = 0, version = version + 1 WHERE sku = 'A100'");
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key-1");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    void expiredPendingReservation_isCancelled_stockReturned_andEventRecorded() throws Exception {
        // Create a reservation, then back-date its expiry to make it stale.
        ResponseEntity<String> create = rest.exchange(url("/api/v1/reservations"),
                HttpMethod.POST,
                new HttpEntity<>("{\"orderId\":\"EXP-1\",\"items\":[{\"sku\":\"A100\",\"quantity\":7}]}", auth()),
                String.class);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        JsonNode root = objectMapper.readTree(create.getBody());
        UUID id = UUID.fromString(root.get("data").get("id").asText());

        // Verify stock dropped.
        Integer afterCreate = jdbc.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku = 'A100'", Integer.class);
        assertThat(afterCreate).isEqualTo(93);

        // Push expires_at into the past.
        jdbc.update("UPDATE reservations SET expires_at = now() - interval '1 minute' WHERE id = ?", id);

        // Trigger the expiry job directly.
        reservationService.findAndExpireReservation(100);

        // Reservation cancelled.
        String status = jdbc.queryForObject(
                "SELECT status FROM reservations WHERE id = ?", String.class, id);
        assertThat(status).isEqualTo("CANCELLED");

        // Stock returned.
        Integer afterExpiry = jdbc.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku = 'A100'", Integer.class);
        assertThat(afterExpiry).isEqualTo(100);

        // A cancellation event was recorded.
        Integer cancelEvents = jdbc.queryForObject(
                "SELECT count(*) FROM reservation_events WHERE reservation_id = ? AND event_type = 'RESERVATION_CANCELLED'",
                Integer.class, id);
        assertThat(cancelEvents).isEqualTo(1);
    }
}
