package com.company.inventory.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Spec coverage for RedisDistributedLockService:
 *  - tryAcquire returns true when SET NX succeeds (key absent)
 *  - tryAcquire returns false when key already held
 *  - tryAcquire returns false (does not throw) when Redis is unavailable
 *  - release deletes the key
 *  - release does not throw when Redis is unavailable
 */
class RedisDistributedLockServiceTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RedisDistributedLockService lockService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        lockService = new RedisDistributedLockService(redis);
    }

    @Test
    void tryAcquire_returnsTrue_whenKeyIsAbsent() {
        when(valueOps.setIfAbsent(eq("lock:expiry-job"), anyString(), any(Duration.class)))
                .thenReturn(true);

        assertThat(lockService.tryAcquire("lock:expiry-job", Duration.ofSeconds(90))).isTrue();
    }

    @Test
    void tryAcquire_returnsFalse_whenKeyAlreadyHeld() {
        when(valueOps.setIfAbsent(eq("lock:expiry-job"), anyString(), any(Duration.class)))
                .thenReturn(false);

        assertThat(lockService.tryAcquire("lock:expiry-job", Duration.ofSeconds(90))).isFalse();
    }

    @Test
    void tryAcquire_returnsFalse_andDoesNotThrow_whenRedisFails() {
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() ->
                assertThat(lockService.tryAcquire("lock:expiry-job", Duration.ofSeconds(90))).isFalse()
        ).doesNotThrowAnyException();
    }

    @Test
    void release_deletesLockKey() {
        lockService.release("lock:expiry-job");

        verify(redis).delete("lock:expiry-job");
    }

    @Test
    void release_doesNotThrow_whenRedisFails() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("connection refused"));

        assertThatCode(() -> lockService.release("lock:expiry-job")).doesNotThrowAnyException();
    }
}