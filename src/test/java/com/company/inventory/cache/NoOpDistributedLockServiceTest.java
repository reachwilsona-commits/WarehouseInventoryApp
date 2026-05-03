package com.company.inventory.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Spec coverage for NoOpDistributedLockService:
 *  - tryAcquire always returns true (single-instance fallback)
 *  - release is a no-op and never throws
 */
class NoOpDistributedLockServiceTest {

    private final NoOpDistributedLockService lockService = new NoOpDistributedLockService();

    @Test
    void tryAcquire_alwaysReturnsTrue() {
        assertThat(lockService.tryAcquire("any-key", Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void release_doesNothingAndDoesNotThrow() {
        assertThatCode(() -> lockService.release("any-key")).doesNotThrowAnyException();
    }
}