package com.company.inventory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.company.inventory.model.error.ApiError;
import com.company.inventory.model.response.ApiResponse;
import com.company.inventory.config.SecurityProperties;
import com.company.inventory.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Filter class to implement Bearer-token-style API key check.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;
    private final Set<String> validKeys;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthFilter(SecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.validKeys = new HashSet<>(properties.apiKeys());
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/health")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(properties.headerName());
        if (key == null || key.isBlank() || !validKeys.contains(key)) {
            writeUnauthorized(response, key == null || key.isBlank()
                    ? "Missing API key header: " + properties.headerName()
                    : "Invalid API key");
            return;
        }
        chain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(ErrorCode.UNAUTHORIZED.httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        var body = ApiResponse.failure(new ApiError(ErrorCode.UNAUTHORIZED.name(), message));
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
