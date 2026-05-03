package com.company.inventory.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("warehouse")
                .withUsername("warehouse")
                .withPassword("warehouse");
        POSTGRES.start();
    }

    @LocalServerPort protected int port;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("app.security.api-keys", () -> "test-key-1,test-key-2");
        registry.add("app.reservation.expiry-job-cron", () -> "-");
        registry.add("nats.enabled",  () -> "false");
        registry.add("redis.enabled", () -> "false");
    }
    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
