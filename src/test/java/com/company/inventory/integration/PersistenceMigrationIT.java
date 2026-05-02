package com.company.inventory.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceMigrationIT extends AbstractIntegrationTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void liquibaseMigrationsApplyCleanly_andSeedDataLoads() {
        Integer products = jdbcTemplate.queryForObject(
                "select count(*) from products", Integer.class);
        assertThat(products).isGreaterThanOrEqualTo(3);

        Integer inventoryRows = jdbcTemplate.queryForObject(
                "select count(*) from inventory", Integer.class);
        assertThat(inventoryRows).isGreaterThanOrEqualTo(3);
    }
}
