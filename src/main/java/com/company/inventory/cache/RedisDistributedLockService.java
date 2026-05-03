package com.company.inventory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisDistributedLockService implements DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(RedisDistributedLockService.class);

    private final StringRedisTemplate redisTemplate;

    public RedisDistributedLockService(StringRedisTemplate redis) {
        this.redisTemplate = redis;
    }

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(acquired);
        } catch (Exception e) {
            log.warn("Redis lock acquire failed key={}, skipping run", key, e);
            return false;
        }
    }

    @Override
    public void release(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis lock release failed key={}", key, e);
        }
    }
}