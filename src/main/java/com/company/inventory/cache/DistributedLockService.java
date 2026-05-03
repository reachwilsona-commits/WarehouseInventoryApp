package com.company.inventory.cache;

import java.time.Duration;

public interface DistributedLockService {
    boolean tryAcquire(String key, Duration ttl);
    void release(String key);
}