package com.company.inventory.cache;

import com.company.inventory.config.RedisProperties;
import com.company.inventory.model.response.InventoryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Class for Read Inventory from cache.
 * Falls back to an empty Optional in case of Redis failure. Callers can load from DB.
 */
@Service
public class InventoryCacheService {

    private static final String KEY_PREFIX = "inventory::";
    private static final Logger log = LoggerFactory.getLogger(InventoryCacheService.class);

    private final ObjectMapper objectMapper;
    @Nullable
    private final StringRedisTemplate redisTemplate;
    private final long ttlSeconds;

    public InventoryCacheService(ObjectMapper objectMapper,
                                  @Autowired(required = false) StringRedisTemplate redis,
                                  RedisProperties props) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redis;
        this.ttlSeconds = props.cacheTtlSeconds();
    }

    public Optional<InventoryResponse> get(String sku) {
        if (redisTemplate == null) return Optional.empty();
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + sku);
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, InventoryResponse.class));
        } catch (Exception e) {
            log.warn("Redis read failed sku={}, falling back to DB", sku, e);
            return Optional.empty();
        }
    }

    public void put(String sku, InventoryResponse response) {
        if (redisTemplate == null) return;
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(KEY_PREFIX + sku, json, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis write failed sku={}", sku, e);
        }
    }

    public void evict(String sku) {
        if (redisTemplate == null) return;
        try {
            redisTemplate.delete(KEY_PREFIX + sku);
        } catch (Exception e) {
            log.warn("Redis evict failed sku={}", sku, e);
        }
    }
}