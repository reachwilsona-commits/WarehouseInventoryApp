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

class ApiSecurityIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void protectedEndpoint_rejectsMissingApiKey_with401() {
        ResponseEntity<String> resp = rest.getForEntity(
                url("/api/v1/inventory/A100"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(resp.getBody()).contains("UNAUTHORIZED");
    }

    @Test
    void protectedEndpoint_rejectsInvalidApiKey_with401() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "wrong-key");
        ResponseEntity<String> resp = rest.exchange(
                url("/api/v1/inventory/A100"), HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_acceptsValidApiKey() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-API-Key", "test-key-1");
        ResponseEntity<String> resp = rest.exchange(
                url("/api/v1/inventory/A100"), HttpMethod.GET, new HttpEntity<>(h), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"sku\":\"A100\"");
    }

    @Test
    void healthEndpoint_isPublic_andReturnsUp() {
        ResponseEntity<String> resp = rest.getForEntity(url("/health"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("\"status\":\"UP\"");
        assertThat(resp.getBody()).contains("\"database\":\"UP\"");
    }
}
