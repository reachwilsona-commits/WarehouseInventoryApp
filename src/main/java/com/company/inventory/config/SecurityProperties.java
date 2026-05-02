package com.company.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Property class for security properties
 * @param apiKeys List<String>
 * @param headerName String
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        List<String> apiKeys,
        String headerName
) {
    public SecurityProperties {
        if (apiKeys == null || apiKeys.isEmpty()) {
            throw new IllegalArgumentException("At least one API key must be configured (app.security.api-keys)");
        }
        if (headerName == null || headerName.isBlank()) {
            headerName = "X-API-Key";
        }
    }
}
