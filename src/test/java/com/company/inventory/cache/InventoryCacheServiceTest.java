package com.company.inventory.cache;

import com.company.inventory.config.RedisProperties;
import com.company.inventory.model.response.InventoryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec coverage for InventoryCacheService:
 *  - get returns cached value on hit, empty on miss, empty on Redis failure
 *  - put stores serialised JSON with TTL; silent on Redis failure
 *  - evict deletes the key; silent on Redis failure
 *  - all operations are no-ops when Redis is not configured (null template)
 */
class InventoryCacheServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private InventoryCacheService cache;

    private static final RedisProperties PROPS =
            new RedisProperties("localhost", 6379, true, 30L, "lock:expiry-job", 90L);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis    = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        cache = new InventoryCacheService(new ObjectMapper(), redis, PROPS);
    }

    @Test
    void get_returnsCachedResponse_onHit() throws Exception {
        InventoryResponse resp = new InventoryResponse("SKU-A", 100, 80, 20);
        String json = new ObjectMapper().writeValueAsString(resp);
        when(valueOps.get("inventory::SKU-A")).thenReturn(json);

        Optional<InventoryResponse> result = cache.get("SKU-A");

        assertThat(result).isPresent();
        assertThat(result.get().sku()).isEqualTo("SKU-A");
        assertThat(result.get().availableStock()).isEqualTo(80);
    }

    @Test
    void get_returnsEmpty_onCacheMiss() {
        when(valueOps.get(anyString())).thenReturn(null);

        assertThat(cache.get("SKU-B")).isEmpty();
    }

    @Test
    void get_returnsEmpty_andDoesNotThrow_whenRedisFails() {
        when(valueOps.get(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> assertThat(cache.get("SKU-C")).isEmpty())
                .doesNotThrowAnyException();
    }

    @Test
    void get_returnsEmpty_whenRedisTemplateIsNull() {
        InventoryCacheService noRedis = new InventoryCacheService(new ObjectMapper(), null, PROPS);

        assertThat(noRedis.get("SKU-D")).isEmpty();
    }

    @Test
    void put_storesSerializedJsonWithTtl() {
        InventoryResponse resp = new InventoryResponse("SKU-A", 100, 80, 20);

        cache.put("SKU-A", resp);

        verify(valueOps).set(eq("inventory::SKU-A"), anyString(), any());
    }

    @Test
    void put_doesNotThrow_whenRedisFails() {
        when(redis.opsForValue()).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> cache.put("SKU-A", new InventoryResponse("SKU-A", 10, 10, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void put_doesNothing_whenRedisTemplateIsNull() {
        InventoryCacheService noRedis = new InventoryCacheService(new ObjectMapper(), null, PROPS);

        assertThatCode(() -> noRedis.put("SKU-A", new InventoryResponse("SKU-A", 10, 10, 0)))
                .doesNotThrowAnyException();
    }

    @Test
    void evict_deletesKeyInRedis() {
        cache.evict("SKU-A");

        verify(redis).delete("inventory::SKU-A");
    }

    @Test
    void evict_doesNotThrow_whenRedisFails() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> cache.evict("SKU-A")).doesNotThrowAnyException();
    }

    @Test
    void evict_doesNothing_whenRedisTemplateIsNull() {
        InventoryCacheService noRedis = new InventoryCacheService(new ObjectMapper(), null, PROPS);

        assertThatCode(() -> noRedis.evict("SKU-A")).doesNotThrowAnyException();
        verify(redis, never()).delete(anyString());
    }
}