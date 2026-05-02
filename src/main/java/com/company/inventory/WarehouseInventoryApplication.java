package com.company.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Class to start the  Warehouse Inventory Application
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WarehouseInventoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(WarehouseInventoryApplication.class, args);
    }
}
