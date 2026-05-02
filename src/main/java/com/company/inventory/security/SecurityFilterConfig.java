package com.company.inventory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.inventory.config.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for Filter registration
 */
@Configuration
public class SecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilter(
            SecurityProperties properties, ObjectMapper objectMapper) {
        FilterRegistrationBean<ApiKeyAuthFilter> reg =
                new FilterRegistrationBean<>(new ApiKeyAuthFilter(properties, objectMapper));
        reg.addUrlPatterns("/*");
        reg.setOrder(1);   // run before any other servlet filter
        reg.setName("apiKeyAuthFilter");
        return reg;
    }
}
