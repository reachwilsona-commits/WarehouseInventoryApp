package com.company.inventory.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PaginationIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpHeaders auth() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key-1");
        return h;
    }

    @Test
    void listWithoutPaginationParams_returns400() {
        ResponseEntity<String> resp = rest.exchange(
                url("/api/v1/reservations"), HttpMethod.GET, new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody()).contains("INVALID_REQUEST");
    }

    @Test
    void listWithPaginationParams_returnsPageStructure() {
        ResponseEntity<String> resp = rest.exchange(
                url("/api/v1/reservations?page=0&size=20"),
                HttpMethod.GET, new HttpEntity<>(auth()), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"page\":0");
        assertThat(resp.getBody()).contains("\"size\":20");
        assertThat(resp.getBody()).contains("\"totalElements\"");
        assertThat(resp.getBody()).contains("\"totalPages\"");
        assertThat(resp.getBody()).contains("\"content\"");
    }
}
