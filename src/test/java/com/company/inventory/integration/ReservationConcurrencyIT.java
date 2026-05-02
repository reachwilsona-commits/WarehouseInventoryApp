package com.company.inventory.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec coverage:
 *  - Concurrent stock test: two simultaneous POSTs combined exceed stock — exactly one 201, one 409
 *  - Concurrent state transition: simultaneous confirm + cancel — exactly one wins
 *  - Concurrent idempotency: same orderId twice — exactly one DB row
 */
class ReservationConcurrencyIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetSeed() {
        // Reset inventory and clear reservations so tests are independent and re-runnable.
        jdbc.update("DELETE FROM reservation_events");
        jdbc.update("DELETE FROM reservation_items");
        jdbc.update("DELETE FROM reservations");
        jdbc.update("UPDATE inventory SET total_stock = 100, available_stock = 100, reserved_stock = 0, version = version + 1 WHERE sku = 'A100'");
        jdbc.update("UPDATE inventory SET total_stock =  50, available_stock =  50, reserved_stock = 0, version = version + 1 WHERE sku = 'B200'");
        jdbc.update("UPDATE inventory SET total_stock =  10, available_stock =  10, reserved_stock = 0, version = version + 1 WHERE sku = 'C300'");
    }

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key-1");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private String body(String orderId, String sku, int qty) {
        return "{\"orderId\":\"" + orderId + "\",\"items\":[{\"sku\":\"" + sku + "\",\"quantity\":" + qty + "}]}";
    }

    @Test
    void concurrentReservationsExceedingStock_oneSuccess_oneRejection() throws Exception {
        // C300 has 10 units. Two requests for 7 each → combined 14 > 10. One must win, one must lose.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        Runnable task1 = () -> {
            try {
                start.await();
                ResponseEntity<String> r = rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                        new HttpEntity<>(body("CONC-A", "C300", 7), auth()), String.class);
                if (r.getStatusCode().value() == 201) created.incrementAndGet();
                if (r.getStatusCode().value() == 409) conflict.incrementAndGet();
            } catch (Exception e) { throw new RuntimeException(e); }
        };
        Runnable task2 = () -> {
            try {
                start.await();
                ResponseEntity<String> r = rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                        new HttpEntity<>(body("CONC-B", "C300", 7), auth()), String.class);
                if (r.getStatusCode().value() == 201) created.incrementAndGet();
                if (r.getStatusCode().value() == 409) conflict.incrementAndGet();
            } catch (Exception e) { throw new RuntimeException(e); }
        };

        pool.submit(task1);
        pool.submit(task2);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(created.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        // The single winner consumed 7 of the 10 units, so 3 remain available.
        Integer available = jdbc.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku = 'C300'", Integer.class);
        assertThat(available).isEqualTo(3);
    }

    @Test
    void concurrentConfirmAndCancel_exactlyOneWins() throws Exception {
        // Create a PENDING reservation.
        ResponseEntity<String> create = rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                new HttpEntity<>(body("STATE-1", "A100", 5), auth()), String.class);
        assertThat(create.getStatusCode().value()).isEqualTo(201);
        JsonNode root = objectMapper.readTree(create.getBody());
        UUID id = UUID.fromString(root.get("data").get("id").asText());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();

        pool.submit(() -> { try {
            start.await();
            ResponseEntity<String> r = rest.exchange(
                    url("/api/v1/reservations/" + id + "/confirm"),
                    HttpMethod.POST, new HttpEntity<>(auth()), String.class);
            if (r.getStatusCode().is2xxSuccessful()) ok.incrementAndGet();
            if (r.getStatusCode().value() == 409) conflict.incrementAndGet();
        } catch (Exception e) { throw new RuntimeException(e); } });

        pool.submit(() -> { try {
            start.await();
            ResponseEntity<String> r = rest.exchange(
                    url("/api/v1/reservations/" + id + "/cancel"),
                    HttpMethod.POST, new HttpEntity<>(auth()), String.class);
            if (r.getStatusCode().is2xxSuccessful()) ok.incrementAndGet();
            if (r.getStatusCode().value() == 409) conflict.incrementAndGet();
        } catch (Exception e) { throw new RuntimeException(e); } });

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        assertThat(ok.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);

        // The reservation is in exactly one terminal state.
        String finalStatus = jdbc.queryForObject(
                "SELECT status FROM reservations WHERE id = ?", String.class, id);
        assertThat(List.of("CONFIRMED", "CANCELLED")).contains(finalStatus);
    }

    @Test
    void concurrentDuplicateOrderId_resultsInExactlyOneRow() throws Exception {
        // Same orderId from two requests at once. One inserts; the other returns the existing row.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Runnable task = () -> { try {
            start.await();
            rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                    new HttpEntity<>(body("IDEMP-1", "A100", 4), auth()), String.class);
        } catch (Exception e) { throw new RuntimeException(e); } };

        pool.submit(task);
        pool.submit(task);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        Integer rowsForOrder = jdbc.queryForObject(
                "SELECT count(*) FROM reservations WHERE order_id = 'IDEMP-1'", Integer.class);
        assertThat(rowsForOrder).isEqualTo(1);

        // And stock was decremented exactly once.
        Integer available = jdbc.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku = 'A100'", Integer.class);
        assertThat(available).isEqualTo(96);
    }

    @Test
    void duplicateOrderIdAfterFact_returns200_andSameReservation() throws Exception {
        ResponseEntity<String> first = rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                new HttpEntity<>(body("DUP-1", "A100", 2), auth()), String.class);
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        UUID id = UUID.fromString(objectMapper.readTree(first.getBody()).get("data").get("id").asText());

        ResponseEntity<String> second = rest.exchange(url("/api/v1/reservations"), HttpMethod.POST,
                new HttpEntity<>(body("DUP-1", "A100", 2), auth()), String.class);
        assertThat(second.getStatusCode().value()).isEqualTo(200);
        UUID idAgain = UUID.fromString(objectMapper.readTree(second.getBody()).get("data").get("id").asText());
        assertThat(idAgain).isEqualTo(id);

        // Stock was decremented only once (started at 100, request was 2 → 98).
        Integer available = jdbc.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku = 'A100'", Integer.class);
        assertThat(available).isEqualTo(98);
    }
}
