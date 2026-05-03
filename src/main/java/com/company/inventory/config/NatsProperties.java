package com.company.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("nats")
public record NatsProperties(
        @DefaultValue("nats://localhost:4222") String url,
        @DefaultValue("RESERVATIONS") String streamName,
        @DefaultValue("false") boolean enabled,
        @DefaultValue("50") int batchSize
) {}