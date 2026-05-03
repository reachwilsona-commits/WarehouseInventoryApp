# Warehouse Inventory Reservation Service

A backend service that manages real-time inventory reservations under concurrent load.
This service is using Java 21, Spring Boot 3.3, PostgresSQL, NATS JetStream, Redis, Liquibase, and TestContainers.

---

## 1. Challenge choice and rationale

I implemented the **Core track plus both optional tracks — Advanced Track A (NATS JetStream) and Advanced Track B (Redis cache + distributed lock)**. The Core track is the foundation: concurrency correctness, idempotency, state machine, and a TestContainers test suite that proves the invariants against a real database. Once the Core was solid, adding NATS and Redis was straightforward because the event boundary and service layer were already designed for extension a new `EventSubscriber` for the outbox relay, a new `InventoryCacheService` for cache-aside reads, zero changes to `ReservationService`. The distributed lock on the expiry job follows naturally from having Redis already wired. Choosing depth on the Core first, then adding the tracks, kept each layer independently testable and verifiable.

---

## 2. Architecture overview

```
src/main/java/com/company/inventory
├── cache/               InventoryCacheService (read-through), DistributedLockService interface,
│                        RedisDistributedLockService, NoOpDistributedLockService
├── config/              @ConfigurationProperties records: ReservationProperties, NatsProperties,
│                        RedisProperties, SecurityProperties; Clock / ObjectMapper / OpenAPI beans;
│                        NatsConfig (stream + consumer setup), RedisConfig
├── controller/          REST endpoints — Reservation, Inventory, Health
├── domain/
│   ├── entity/          JPA aggregates: Product, Inventory, Reservation, ReservationItem,
│   │                    ReservationEvents, ReservationStatus
│   ├── event/           DomainEvent hierarchy (Created / Confirmed / Cancelled),
│   │                    EventType, CancellationReason, NatsSubject, NatsEventEnvelope
│   ├── factory/         ReservationFactory — Factory pattern
│   └── state/           ReservationState + Pending / Confirmed / Cancelled + ReservationStateFactory
├── exception/           BusinessException hierarchy + ErrorCode enum + GlobalExceptionHandler
├── log/                 StateTransitionLogger — structured JSON audit trail
├── messaging/
│   ├── nats/            NatsOutboxJob (outbox relay to NATS), NatsInventoryEventConsumer
│   ├── publisher/       EventPublisher interface + DomainEventPublisher (Observer fan-out)
│   └── subscribers/     EventSubscriber, OutboxPersistenceSubscriber, SubscriberLogging
├── model/               Request / response / error records (DTOs only, no logic)
├── repository/          Spring Data JPA; all lock annotations and native queries live here
├── security/            ApiKeyAuthFilter + SecurityFilterConfig
└── service/             ReservationService, InventoryService, ReservationExpiryJob,
                         ReservationCommand, ReservationResult
```

Each layer has one job. `controller/` speaks HTTP. `service/` owns transactions and orchestration. `domain/` holds business rules. `repository/` owns SQL strategy. `messaging/` is the event boundary — producers never know who is listening. `cache/` owns all Redis interactions so Redis failure never leaks into service logic. `log/` writes the structured audit trail on every state change.

---

## 3. Framework choice

**Spring Boot 3.3 on Java 21.**

Spring Data JPA gives `@Lock(PESSIMISTIC_WRITE)` as a single repository annotation — the locking strategy is declarative and visible in one place. Spring's transaction management lets confirm and cancel share a `cancelInternal` helper that is always called inside an active transaction. `@Scheduled` handles the expiry cron job with no external scheduler. `@ConditionalOnProperty` wires NATS and Redis beans only when their respective feature flags are enabled, so the same artifact can run in environments without those services. Spring Boot Testcontainers makes integration tests one annotation away from real Postgres. Quarkus would have been fine; Spring's depth of JPA locking idioms and its first-class conditional bean model tipped the choice for a service that has multiple optional infrastructure dependencies.

---

## 4. Design patterns

| Pattern | Location | Why it fits |
|---|---|---|
| **State** | `domain/state/ReservationState.java` and `Pending/Confirmed/CancelledState.java` | Each state knows which transitions it permits. The abstract base throws `InvalidStateTransitionException` by default; subclasses override only what is legal. Service code never branches on `ReservationStatus`. |
| **Factory** | `domain/factory/ReservationFactory.java` | A reservation must always have a UUID, start in PENDING, and carry an `expiresAt` computed from the configured TTL. Centralising construction prevents that logic from drifting across the controller, expiry job, and tests. |
| **Observer** | `messaging/publisher/EventPublisher.java`, `DomainEventPublisher.java`, and all `EventSubscriber` implementations | Subscribers are auto-discovered via Spring's `List<EventSubscriber>` injection. Adding the NATS outbox relay, the outbox persistence subscriber, and the structured-log subscriber each required a new class implementing one method — zero changes to `ReservationService`. |

---

## 5. SOLID principles

- **Single Responsibility** — `StateTransitionLogger` does one thing: write the structured audit row. `InventoryCacheService` does one thing: wrap Redis with fallback. Each subscriber (`OutboxPersistenceSubscriber`, `SubscriberLogging`, `NatsOutboxJob`) has its own reason to change.
- **Open/Closed** — `ReservationState` is open for new states (add a subclass) without modifying existing ones. `EventSubscriber` is open for new delivery channels (NATS, Kafka, webhook) without touching the reservation service.
- **Dependency Inversion** — `ReservationService` depends on `EventPublisher` (interface) and `InventoryCacheService` (concrete but injected). `ReservationExpiryJob` depends on `DistributedLockService` (interface) — it receives `NoOpDistributedLockService` when Redis is off, `RedisDistributedLockService` when Redis is on, without any conditional inside the job itself.
- **Interface Segregation** — `EventSubscriber` exposes a single `onEvent(DomainEvent)` method. `DistributedLockService` exposes `tryAcquire` and `release` only.
- **Liskov** — every `ReservationState` subclass either performs the transition or throws `InvalidStateTransitionException`. `NoOpDistributedLockService` is a valid substitute for `DistributedLockService` — callers never know the difference.

---

## 6. Database design decisions

| Table | Notable columns | Reasoning |
|---|---|---|
| `inventory` | `available_stock`, `reserved_stock`, `total_stock`, `version` | Three-column stock model makes oversell detection a simple `available_stock >= requested` check. `version` supports optimistic locking on read-only paths. A `CHECK (available_stock + reserved_stock = total_stock)` constraint catches application bugs at the DB layer. |
| `reservations` | `order_id VARCHAR(128) UNIQUE`, `status`, `expires_at` | `UNIQUE` on `order_id` is the atomic idempotency guard. Two concurrent inserts for the same order cannot both succeed. |
| `reservation_items` | `UNIQUE (reservation_id, sku)`, `quantity > 0` CHECK | Prevents duplicate SKUs on one reservation; factory coalesces before insert, constraint is the safety net. |
| `reservation_events` | `payload JSONB`, `published_at TIMESTAMPTZ NULL` | Outbox pattern. Written in the same transaction as the state change; `published_at IS NULL` is the work queue for the NATS relay. |

**Indexes** (`006-add-indexes.sql`): partial index on `WHERE status = 'PENDING'` for the expiry query; partial index on `WHERE published_at IS NULL` for the outbox drainer; composite index on `(status, created_at)` for the list endpoint filter.

**Locking strategy** — discussed in §7.

**Trade-offs**: UUID v4 for reservation IDs (no monotonic insert hot spot, but slightly worse index locality than v7 — would switch to v7 when JDK support stabilises). `BIGSERIAL` on items and events tables (stable surrogate key, minor sequence overhead).

---

## 7. Concurrency strategy

**Stock reservation — pessimistic locking.** `InventoryRepository.findBySkuInOrderBySkuAsc` acquires a `SELECT ... FOR UPDATE` write lock on every affected inventory row before checking availability. SKUs are sorted lexicographically so every transaction acquires locks in the same order, preventing deadlocks. The all-or-nothing pre-check runs against locked rows, deductions are applied, then the reservation is persisted — all in one transaction.

Pessimistic was chosen over optimistic because contention is the expected case for a reservation system. Under a stampede, optimistic locking means most concurrent transactions would see a stale `version`, retry, and see it again — wasted work proportional to contention. Pessimistic locking serializes contended rows and lets uncontended SKUs proceed in parallel.

**State transitions — same pattern.** `findByIdForUpdate` locks the reservation row before any state machine call, so confirm and cancel are atomic.

**Expiry job vs. API** — the expiry query uses `FOR UPDATE SKIP LOCKED`. If an API transaction is mid-flight on a row, the job skips it; the next cron tick picks it up if it is still expired and unlocked. No coordination protocol needed.

---

## 8. Idempotency implementation

Three layers engage in order:

1. **Fast path** — `findByOrderId` lookup on the way in. If the row already exists, return it as a duplicate immediately; no locks taken.
2. **Atomic guard** — the `reservations.order_id UNIQUE` constraint. If two simultaneous requests both pass the fast-path check, exactly one INSERT succeeds; the other raises `DataIntegrityViolationException`.
3. **Recovery** — the loser is caught in the controller, opens a fresh `REQUIRES_NEW` read transaction via `fetchExistingByOrderId`, and returns the committed row. Its own stock deductions and outbox event were rolled back, so stock cannot be double-decremented.

Wire contract: new reservation → `HTTP 201 Created`. Duplicate orderId → `HTTP 200 OK` with existing data plus `X-Idempotent-Replay: true` and `X-Error-Code: DUPLICATE_ORDER` headers.

---

## 9. Event design — Advanced Track A (NATS JetStream)

Domain events are a sealed hierarchy under `DomainEvent`. The reservation service publishes them through `EventPublisher`; subscribers are auto-discovered via `List<EventSubscriber>`.

**In-process subscribers:**
- `OutboxPersistenceSubscriber` — writes to `reservation_events` in the same transaction as the state change. Events are durable before any external delivery is attempted.
- `SubscriberLogging` — emits a structured JSON log line per event.

**NATS outbox relay (`NatsOutboxJob`):**
- Runs on a cron schedule (`nats.job-cron`, default every minute).
- Reads unpublished rows from `reservation_events WHERE published_at IS NULL ORDER BY id ASC` in configurable batches.
- Builds a `NatsEventEnvelope` (eventType, reservationId, orderId, timestamp, payload) and publishes to one of three subjects: `reservations.created`, `reservations.confirmed`, `reservations.cancelled`.
- Sets `Nats-Msg-Id` header to `{reservationId}.{eventType}.{rowId}` for server-side deduplication within the NATS dedup window.
- Marks `published_at` only after receiving a `PublishAck` from NATS, ensuring at-least-once delivery.

**NATS stream configuration (`NatsConfig`):**
- Stream name: `RESERVATIONS`, subjects: `reservations.*`, storage: file, retention: limits.
- Error code 10058 (stream already exists) is tolerated on startup so restarts are safe.

**NATS consumer (`NatsInventoryEventConsumer`):**
- Durable push consumer: `warehouse-audit-consumer`, `AckPolicy.Explicit`, `DeliverPolicy.All`, 30 s ack-wait, max 5 redeliveries.
- Bounded LRU map (capacity 1000) deduplicates by stream sequence number in-process.
- On successful processing: `msg.ack()`. On parse failure: `msg.nak()` so NATS redelivers up to the max-deliver limit.

**When NATS is unavailable:** all NATS beans are gated by `@ConditionalOnProperty(name="nats.enabled", havingValue="true")`. With `nats.enabled=false` the application runs without touching NATS — no bean created, no connection attempted. Outbox rows accumulate until NATS is restored.

---

## 10. Redis design — Advanced Track B

**Cache key strategy** — `inventory::{sku}` (e.g. `inventory::A100`). Implemented in `InventoryCacheService.KEY_PREFIX`.

**TTL** — 30 seconds, configured via `redis.cache-ttl-seconds`. Short enough that stale stock data expires quickly; long enough to absorb a read spike on a popular SKU.

**Cache population** — lazy read-through in `InventoryService.getInventoryBySku()`. On a cache miss: read from Postgres, serialize the `InventoryResponse` record to JSON, write to Redis with TTL, return. No startup pre-warming.

**Cache invalidation** — synchronous `DEL` call inside the write transaction in `ReservationService`, immediately after `reserve()` or `release()` modifies a SKU's available stock. This is optimistic invalidation — the write has not yet committed when the delete runs, but since we're deleting (not updating), the worst outcome is an unnecessary cache miss, not stale data.

**Fallback behaviour** — `InventoryCacheService` holds a `@Nullable StringRedisTemplate`. If Redis is disabled (`redis.enabled=false`) the template is null and all operations are no-ops. If Redis goes down at runtime, every Redis call is wrapped in a try/catch that logs a warning and returns `Optional.empty()`, falling through to Postgres. Redis unavailability never propagates an exception to the caller.

**Distributed lock for expiry job** — `DistributedLockService` interface with two implementations:
- `RedisDistributedLockService` — `SET NX PX` via `setIfAbsent(key, "1", ttl)` with zero-second wait. Returns `false` if the key is already held, so competing instances skip rather than block.
- `NoOpDistributedLockService` — always returns `true`; used when Redis is disabled, so the job still runs (single-instance fallback).

Lock key: `lock:expiry-job` (configurable via `redis.expiry-job-lock-key`). Lock TTL: 90 seconds (`redis.expiry-job-lock-ttl-seconds`), shorter than the 2-minute job interval. The lock is always released in a `finally` block.

---

## 11. Expiry job design

`ReservationExpiryJob` runs every 2 minutes (`app.reservation.expiry-job-cron`). On each tick:

1. Attempts to acquire the distributed lock (`lock:expiry-job`, 90 s TTL). Skips the run if the lock is already held by another instance.
2. Calls `findAndExpireReservation(batchSize)` in a loop until the batch is smaller than `batchSize`, draining the full backlog.
3. Releases the lock in a `finally` block.

The underlying query uses `FOR UPDATE SKIP LOCKED`, so even without the Redis lock, concurrent instances grab disjoint sets of rows and never double-process a reservation. The Redis lock adds a coarser guarantee: only one instance runs the full loop at a time, useful for rate-limiting the load on Postgres.

When Redis is unavailable `NoOpDistributedLockService` is injected and the lock step is transparent — all instances run, `SKIP LOCKED` keeps correctness.

---

## 12. Security approach

A single servlet filter, `ApiKeyAuthFilter`, reads the `X-API-Key` header and compares it against a `Set<String>` built from `app.security.api-keys` (comma-separated, overridable via `APP_SECURITY_API_KEYS` env var). Missing or invalid keys return `401 UNAUTHORIZED` in the standard `ApiError` envelope. Allow-listed paths (no key required): `/health`, `/v3/api-docs/**`, `/swagger-ui/**`.

The filter is registered via `FilterRegistrationBean` (not as a Spring bean) to avoid double-registration. `spring-boot-starter-security` was intentionally excluded — the spec asks for an API key check, not an identity model, and a 70-line filter is easier to audit than the security DSL for this use case.

---

## 13. How to run the system

```bash
# Start Postgres, NATS, and Redis
docker build -t inventory-app:1.0 .
docker compose up -d

# Run the application (NATS and Redis enabled by default)
./mvnw spring-boot:run

# Or run with Redis and NATS disabled (Core only)
NATS_ENABLED=false REDIS_ENABLED=false ./mvnw spring-boot:run
```

| URL | Description |
|---|---|
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/v3/api-docs | OpenAPI spec |
| http://localhost:8080/health | Health check |
| http://localhost:8082 | pgAdmin (admin@example.com / adminpassword) |
| localhost:4222 | NATS (JetStream enabled) |
| localhost:6379 | Redis |

Default API key: `dev-api-key-001` (override via `APP_SECURITY_API_KEYS`).

```bash
# Reserve stock
curl -s -X POST http://localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dev-api-key-001' \
  -d '{"orderId":"ORD-1001","items":[{"sku":"A100","quantity":5}]}'

# Check inventory (served from Redis cache after first hit)
curl -s http://localhost:8080/api/v1/inventory/A100 \
  -H 'X-API-Key: dev-api-key-001'
```

---

## 14. How to run the tests

```bash
# Unit tests only — no Docker required, fast
./mvnw test

# Unit + integration tests — Testcontainers spins up Postgres (Docker must be running)
./mvnw verify
```

Unit tests mock all infrastructure (Postgres, Redis, NATS). Integration tests use a shared static `PostgreSQLContainer`; NATS and Redis are disabled via `@DynamicPropertySource` in `AbstractIntegrationTest`. Coverage gate: **85% instruction coverage** enforced by the JaCoCo `check` goal during `verify`.

---

## 15. Trade-offs made

- **Optimistic-inside-pessimistic invalidation.** Cache eviction runs inside the write transaction, before commit. The window where a reader could cache pre-commit data is tiny (milliseconds), and since we DELETE rather than update, the worst outcome is an extra Postgres read, not stale data being served. A stricter approach would use `TransactionSynchronizationManager.afterCommit`, at the cost of more complexity.
- **In-process event dispatch.** Subscribers run synchronously inside the reservation transaction. This is a feature for `OutboxPersistenceSubscriber` (atomicity) and a small risk for logging-only subscribers (a slow logger could slow the request). Accepted for the scope of this exercise.
- **Outbox relay as a scheduled poll.** `NatsOutboxJob` polls on a cron schedule rather than reacting to a DB `LISTEN/NOTIFY`. This introduces up to 1 minute of delivery latency and adds a Postgres read every minute even when idle. `LISTEN/NOTIFY` would be the right follow-up.
- **No rate limiting.** Not in the spec. Would be the next addition before production.
- **API keys held in memory.** Adequate for the exercise; a real deployment would back this with a secret store and support hot-reload.
- **UUID v4 for reservation IDs.** Correct, but slightly worse insert locality than UUID v7. Would switch once JDK support stabilises.

---

## 16. What would break at 10,000 reservation requests per minute

10k/min ≈ 167 rps sustained. The first failure depends on SKU distribution.

**Hot single SKU (worst case).** Pessimistic row-level locking on `inventory` serializes every request touching the same SKU. At 167 rps with a 5–15 ms transaction (DB roundtrip + event write + outbox insert), a single row can service roughly 65–200 transactions per second. Beyond that, transactions queue behind the lock; p99 latency climbs from milliseconds into hundreds of milliseconds; the HikariCP pool (capped at 20) exhausts; the API returns 500s as connection acquisition times out.

Fix: partition the hot SKU into N stock buckets (`inventory_buckets(sku, bucket_id, stock)`). Pick a bucket per request by hashing `orderId`. Reconcile across buckets asynchronously.

**Even SKU spread.** Postgres handles 167 rps comfortably on commodity hardware, but write amplification accumulates: each reservation writes 1 reservation row + N item rows + N inventory updates + 1 outbox event. The HikariCP pool at 20 connections becomes the ceiling at roughly `20 / mean_tx_time_seconds` rps.

Fix: tune the Hikari pool to `(vCPUs × 2 + disk spindles)` ≈ 50 for a typical production node; ensure the Redis cache absorbs all inventory read traffic so reads and writes don't compete for connections.

**Expiry job.** If the PENDING backlog grows faster than the job drains it (100 rows / 2 min = ~0.8 rows/s), the job runs long and overlaps with the next tick. The distributed lock prevents concurrent runs; the `SKIP LOCKED` query prevents double-processing; but backlog growth still degrades reservation latency.

Fix: make batch size and cron interval tunable; alert on backlog age via a Micrometer gauge on `reservation_events WHERE published_at IS NULL`.

**Most likely first failure:** HikariCP exhaustion on a hot SKU. Fix order: (1) add `reserve_lock_wait_ms` per-SKU metrics and alert at p95 > 100 ms; (2) expand the pool to 50; (3) shard hot SKUs; (4) Redis read-through cache is already in place, ensuring read traffic never competes with writes for connections.

---

## Appendix: Files of interest

| Concern | Location |
|---|---|
| State pattern | `domain/state/` |
| Factory pattern | `domain/factory/ReservationFactory.java` |
| Observer / fan-out | `messaging/publisher/` and `messaging/subscribers/` |
| Pessimistic locking | `repository/InventoryRepository.java`, `ReservationRepository.java` |
| Idempotency | `service/ReservationService.createReservation`, `controller/ReservationController.create`, `003-create-reservations.sql` |
| NATS outbox relay | `messaging/nats/NatsOutboxJob.java` |
| NATS consumer | `messaging/nats/NatsInventoryEventConsumer.java` |
| Redis cache | `cache/InventoryCacheService.java` |
| Distributed lock | `cache/DistributedLockService.java`, `cache/RedisDistributedLockService.java` |
| Structured audit trail | `log/StateTransitionLogger.java`, `resources/logback-spring.xml` |
| Liquibase changesets | `resources/db/changelog/changes/` |
| Concurrency integration tests | `integration/ReservationConcurrencyIT.java` |