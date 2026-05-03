package com.company.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("redis")
public record RedisProperties(
        @DefaultValue("localhost") String host,
        @DefaultValue("6379")      int port,
        @DefaultValue("false")     boolean enabled,
        @DefaultValue("30")        long cacheTtlSeconds,
        @DefaultValue("lock:expiry-job") String expiryJobLockKey,
        @DefaultValue("90")        long expiryJobLockTtlSeconds
) {}