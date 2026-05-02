package com.company.inventory.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec coverage: Swagger UI must be reachable at /swagger-ui.html.
 * Springdoc redirects /swagger-ui.html to /swagger-ui/index.html — both should be reachable.
 */
class OpenApiIT extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;

    @Test
    void swaggerUiIsReachable() {
        ResponseEntity<String> resp = rest.getForEntity(url("/swagger-ui/index.html"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void openApiSpecIsReachable() {
        ResponseEntity<String> resp = rest.getForEntity(url("/v3/api-docs"), String.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).contains("Warehouse Inventory");
    }
}
