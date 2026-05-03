package com.company.inventory.cache;

import java.time.Duration;

public class NoOpDistributedLockService implements DistributedLockService {

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        return true;
    }

    @Override
    public void release(String key) {
        // no distributed lock when Redis is not available
    }
}