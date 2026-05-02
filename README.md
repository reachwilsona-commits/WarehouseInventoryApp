# Warehouse Inventory Reservation Service

A backend service that manages real-time inventory reservations under concurrent load. Implements the **Core track** of the Fortna WES Engineering Principal Engineer take-home: Java 21, Spring Boot 3, PostgreSQL, Liquibase, OpenAPI, Docker Compose, unit tests, and Testcontainers integration tests.

---

## 1. Challenge choice and rationale

I built only the Core track. Two days of solo engineering buys depth in one place or breadth across many; I chose depth on the things the assignment graded explicitly — concurrency correctness, idempotency, the State / Factory / Observer patterns, and a Testcontainers-backed test suite that proves both. Spending the same hours wiring NATS JetStream and Redis on top would have meant shallower invariants on the bits that matter most. The event boundary is designed so that adding a NATS adapter later is one new `EventSubscriber` class — zero changes to the reservation service — which is the spirit of the optional tracks.

## 2. Architecture overview

```
src/main/java/com/company/inventory
├── config/               @ConfigurationProperties + Clock/ObjectMapper/OpenAPI beans
├── controller/           REST endpoints (Reservation, Inventory, Health)
├── domain/
│   ├── entity/           JPA aggregates: Product, Inventory, Reservation, ReservationItem, ReservationEvents, ReservationStatus
│   ├── event/            DomainEvent hierarchy (Created/Confirmed/Cancelled) + EventType + CancellationReason
│   ├── factory/          ReservationFactory — Factory pattern entry point
│   └── state/            ReservationState + Pending/Confirmed/Cancelled — State pattern + ReservationStateFactory
├── exception/            BusinessException hierarchy + ErrorCode → HTTP-status enum + GlobalExceptionHandler
├── log/                  StateTransitionLogger — structured audit trail per spec
├── messaging/
│   ├── publisher/        EventPublisher interface (Observer producer) + DomainEventPublisher
│   └── subscribers/      EventSubscriber + OutboxPersistenceSubscriber + SubscriberLogging
├── model/
│   ├── error/            ApiError envelope
│   ├── request/          CreateReservationRequest, RequestedItem
│   └── response/         ApiResponse, HealthResponse, InventoryResponse, PageResponse, ReservationResponse
├── repository/           Spring Data JPA; pessimistic locks live here, not in services
├── security/             ApiKeyAuthFilter (servlet filter) + SecurityFilterConfig
└── service/              ReservationService, InventoryService, ReservationExpiryJob, ReservationCommand, ReservationResult
```

Layer responsibilities, in one line each:

- **controller/** speaks HTTP and validates input; it never touches the database directly.
- **service/** orchestrates a single transaction per request; owns concurrency control.
- **domain/** is the only place where business rules live (state machine + invariants).
- **repository/** owns SQL strategy (locks, skip-locked, query shape).
- **messaging/** is the event boundary; producers don't know about subscribers.
- **log/** writes the mandated structured log entry on every state change.

The reservation service depends on the **producer** half of the event boundary (`EventPublisher`) and on no individual subscriber, so we can plug in NATS, Kafka, log-only, or a test capture by adding one bean.

## 3. Framework choice

**Spring Boot 3.3 on Java 21.** Reasons:

1. **Spring Data JPA** gives us `@Lock(PESSIMISTIC_WRITE)` on a single repository annotation — the locking strategy is declarative and reviewable in one place.
2. **Spring's transaction management** lets the cancel/expiry paths share a single `@Transactional(propagation = MANDATORY)` helper, which guarantees the row-level lock and the state mutation always live in the same transaction.
3. **springdoc-openapi** generates Swagger UI at `/swagger-ui.html` with no code beyond the `OpenApiConfig` bean.
4. **`@Scheduled` + `@EnableScheduling`** is enough for the expiry job — combined with `FOR UPDATE SKIP LOCKED` we don't need an external scheduler or a distributed lock.
5. **Spring Boot Testcontainers** makes integration tests one annotation away from a real Postgres.

Quarkus would have been fine; Spring's depth of Postgres-locking idioms (the JPA `@QueryHints`, `@Lock`, native queries with `SKIP LOCKED`) tipped the choice.

## 4. Design patterns

All three are required. Each links to the exact source file.

| Pattern | File | Why it fits |
|---|---|---|
| **State** | [`ReservationState.java`](src/main/java/com/company/inventory/domain/state/ReservationState.java), [`PendingState.java`](src/main/java/com/company/inventory/domain/state/PendingState.java), [`ConfirmedState.java`](src/main/java/com/company/inventory/domain/state/ConfirmedState.java), [`CancelledState.java`](src/main/java/com/company/inventory/domain/state/CancelledState.java), [`ReservationStateFactory.java`](src/main/java/com/company/inventory/domain/state/ReservationStateFactory.java) | Each state knows which transitions it permits; the default `confirm()`/`cancel()` on the abstract class throws `InvalidStateTransitionException`, and concrete subclasses override only what's legal. The service layer never branches on `ReservationStatus`. Try the test `ReservationStateTransitionTest` — it walks every legal and illegal edge of the lifecycle. |
| **Factory** | [`ReservationFactory.java`](src/main/java/com/company/inventory/domain/factory/ReservationFactory.java) | A reservation has three things that must be set consistently every time: a UUID, an initial state of PENDING, and an `expiresAt` derived from the configured TTL. Centralising construction prevents drift across the controller, the scheduled job, and tests. The factory also coalesces duplicate SKUs in the same request before they hit the unique-key constraint on `reservation_items`. |
| **Observer** | [`EventPublisher.java`](src/main/java/com/company/inventory/messaging/publisher/EventPublisher.java), [`DomainEventPublisher.java`](src/main/java/com/company/inventory/messaging/publisher/DomainEventPublisher.java), [`EventSubscriber.java`](src/main/java/com/company/inventory/messaging/subscribers/EventSubscriber.java), [`OutboxPersistenceSubscriber.java`](src/main/java/com/company/inventory/messaging/subscribers/OutboxPersistenceSubscriber.java), [`SubscriberLogging.java`](src/main/java/com/company/inventory/messaging/subscribers/SubscriberLogging.java) | Subscribers are auto-discovered via Spring's `List<EventSubscriber>` injection. Adding a NATS-publishing subscriber is one new class implementing `EventSubscriber` — zero changes to `ReservationService` or its tests. The unit test `DomainEventPublisherTest` exercises the contract directly. |

## 5. SOLID principles in this codebase

- **Single Responsibility** — `StateTransitionLogger` does one thing: writes the audit row. `ReservationFactory` does one thing: builds aggregates. The persistence and the structured-log subscriber are separate classes even though both react to the same events, because they have separate reasons to change.
- **Open/Closed** — `ReservationState` is open for extension (add a new subclass) and closed for modification (existing subclasses don't change when a new state appears). Same shape for `EventSubscriber`: adding NATS doesn't require touching any existing class.
- **Dependency Inversion** — `ReservationService` depends on the `EventPublisher` interface, not on `DomainEventPublisher` or any subscriber. The `Clock` is injected so tests can pin time without monkey-patching `OffsetDateTime.now()`.
- **Interface Segregation** — `EventSubscriber` exposes a single `onEvent(DomainEvent)` method; subscribers don't have to implement methods they don't care about.
- **Liskov** — every subclass of `ReservationState` honours the contract: it either performs the transition or throws `InvalidStateTransitionException`. No subclass weakens the contract by, say, silently no-op'ing.

## 6. Database design decisions

| Table | Notable columns | Why |
|---|---|---|
| `products` | `sku VARCHAR(64) PK` | SKU is the natural key referenced by every other table. Bounded length keeps indexes compact. |
| `inventory` | `total_stock`, `available_stock`, `reserved_stock`, `version`, plus a `CHECK` constraint that `available + reserved = total` | The CHECK is a paranoid invariant — it catches application bugs at the DB layer. `version` enables JPA optimistic locking in the rare paths that don't take a pessimistic lock. |
| `reservations` | `id UUID PK`, `order_id VARCHAR(128) UNIQUE`, `status` with CHECK, `expires_at` | The UNIQUE on `order_id` is the **atomic** guard for idempotent POST: two simultaneous inserts with the same orderId cannot both succeed; the loser is translated into "return existing reservation". |
| `reservation_items` | `UNIQUE (reservation_id, sku)`, `quantity > 0` CHECK | Prevents the same SKU being listed twice on the same reservation. The factory coalesces duplicates before insert, but the constraint is the safety net. |
| `reservation_events` | `payload JSONB`, `published_at TIMESTAMPTZ NULL` | Outbox pattern. Written transactionally with the state change, so we cannot lose an event. `published_at NULL` lets a future drainer (NATS, Kafka) safely retry unpublished rows. |

**Indexes** (in `006-add-indexes.sql`):

- `idx_reservations_status_created` — supports the `?status=PENDING` filter on the list endpoint with `ORDER BY created_at DESC`.
- `idx_reservations_pending_expires` — **partial** index on `WHERE status = 'PENDING'`, used by the expiry job. A partial index keeps the index tiny because confirmed/cancelled rows accumulate; the hot scan stays fast.
- `idx_reservation_items_reservation` — FK lookup during cancel/expiry.
- `idx_reservation_events_unpublished` — partial index on `WHERE published_at IS NULL` so an outbox drainer scans only the work queue, not the full event history.

**Locking strategy** — covered in §7.

**Trade-offs taken**: `BIGSERIAL` on `reservation_items` and `reservation_events` (sequence allocation overhead, but a stable surrogate key beats compound natural keys for the events table). UUID v4 chosen for reservation `id` (random, no monotonic-insert hot spot — but slightly worse index locality than v7. With more time I'd switch to v7 once the JDK / library support is broader).

## 7. Concurrency strategy

**Stock reservation** uses **pessimistic row-level locking** (`SELECT ... FOR UPDATE`). The reservation service:

1. Sorts the requested SKUs lexicographically, then
2. Calls [`InventoryRepository.findBySkuInOrderBySkuAsc`](src/main/java/com/company/inventory/repository/InventoryRepository.java) which acquires a write lock on each row,
3. Performs an **all-or-nothing pre-check** against the locked rows,
4. Applies deductions, persists the reservation.

Sorting matters: it gives every transaction the same lock-acquisition order and prevents deadlocks (`TX-A` locks A then B; `TX-B` locks B then A → deadlock). With sorting, `TX-B` waits for `TX-A` to release A before continuing.

I chose **pessimistic** over **optimistic** for the stock path because contention is the point — when the system is under stress, lots of requests try to grab the same SKU at once. With optimistic locking, a stampede on `A100` would mean almost every retry sees a stale `version`, retries, sees it again, retries again — wasted work proportional to contention. Pessimistic locking serializes contended rows and lets uncontended SKUs proceed in parallel.

The `inventory.version` column is still useful: paths that don't take a pessimistic lock (event subscribers, future read-through cache writers) get optimistic-lock failure detection for free.

**State transitions** (confirm/cancel) use the same `SELECT ... FOR UPDATE` pattern on the `reservations` row, so the state mutation and the event write happen in one atomic transaction. The State pattern (rather than service-layer if/else) makes it impossible to forget a transition check.

**Expiry job vs. API** — the expiry job uses [`findExpired`](src/main/java/com/company/inventory/repository/ReservationRepository.java) with `FOR UPDATE SKIP LOCKED`. If a row is currently locked by an API confirm/cancel transaction, the job skips it; on the next tick, if it's still PENDING and still expired, it will be picked up. This satisfies the "expiry job must not race with API" requirement automatically.

## 8. Idempotency implementation

Three layers, in order of how they engage:

1. **Fast path** — `findByOrderId` lookup on the way in. If the row already exists, we return it as a duplicate immediately, no locks taken.
2. **Atomic guard** — the `reservations.order_id UNIQUE` constraint. If two simultaneous requests both pass the fast-path check, exactly one INSERT will succeed; the other raises `DataIntegrityViolationException`.
3. **Recovery** — the loser is caught in the controller (`IdempotentRetryException`), opens a fresh `REQUIRES_NEW` transaction via `fetchExistingByOrderId`, and returns the now-committed row to the client.

The loser's failed transaction rolls back **both** its own stock deductions and its own outbox event. So at most one stock-deduction event was ever recorded, and stock cannot be double-decremented.

Wire contract:

- New reservation → `HTTP 201 Created`.
- Duplicate orderId → `HTTP 200 OK` with the existing reservation in `data`. Headers `X-Idempotent-Replay: true` and `X-Error-Code: DUPLICATE_ORDER` flag the replay so observability tools can count duplicates without parsing bodies.

## 9. Event design

Domain events are a hierarchy under `DomainEvent` (`ReservationCreatedEvent`, `ReservationConfirmedEvent`, `ReservationCancelledEvent`). The reservation service publishes them through `EventPublisher`; subscribers are auto-discovered.

Two subscribers ship in the box:

- **`OutboxPersistenceSubscriber`** — writes a row into `reservation_events` in the same transaction as the state change. This is the **outbox pattern**: events are durable even if the process dies before any other subscriber processes them.
- **`SubscriberLogging`** — emits a structured JSON log line per event for observability.

Adding a NATS publisher (Advanced track A) would be one new class implementing `EventSubscriber`. The reservation service does not change. A drainer would scan `reservation_events WHERE published_at IS NULL ORDER BY id ASC`, publish to NATS with `MsgID = id` for at-least-once de-duplication on the consumer side, then UPDATE `published_at`.

## 10. Redis design

Not implemented — Advanced track B was not selected. Notes for what I'd do:

- **Keys** — `inventory:{sku}` storing the `InventoryResponse` JSON. TTL 30 s as the spec suggests.
- **Cache stampede** — single-flight via Redis SETNX or Caffeine local mutex inside a Redis fallback layer.
- **Invalidation** — synchronous `DEL` on every commit that mutated stock, with a `TransactionSynchronizationManager.afterCommit` hook so we never invalidate before the row is durable.
- **Distributed lock** — Redisson `RLock("lock:expiry-job")` with leaseTime ≪ jobInterval (e.g. 60 s lease for a 120 s interval) and `tryLock(0, leaseTime)` so competing workers skip rather than block.
- **Fallback** — a `try/catch (RedisConnectionException)` → log warn → fall back to Postgres read-through. Health check would still report `UP` because Redis is a cache, not a dependency.

## 11. Expiry job design (multi-instance safety)

`ReservationExpiryJob` runs every 2 minutes via `@Scheduled(cron = ...)`. Inside, it calls `ReservationService.findAndExpireReservation(batchSize)`, whose query is:

```sql
select * from reservations
 where status = 'PENDING' and expires_at < :now
 order by expires_at asc
 limit :limit
 for update skip locked
```

The `SKIP LOCKED` is the multi-instance guarantee. If two app pods run the job simultaneously, each grabs an unlocked batch; concurrent runs see disjoint sets of rows. No row is processed twice in the same window.

I deliberately **avoided** a global cluster-lock (Redis, ZooKeeper, lease tables). Reasons:

- `SKIP LOCKED` is sufficient for correctness with no extra moving parts.
- It scales horizontally — multiple instances share the work instead of one being idle.
- It requires zero infra beyond Postgres, which we already have.

If we later need the job to run on exactly one instance for rate-limiting or audit reasons, swap to Postgres `pg_try_advisory_lock(JOB_ID)` or a Redis lock — same idea, different fence.

The same row-level locks taken by API confirm/cancel mean the expiry job cannot race with a concurrent user action on the same reservation.

## 12. Security approach

A single servlet filter, [`ApiKeyAuthFilter`](src/main/java/com/company/inventory/security/ApiKeyAuthFilter.java):

- Reads the `X-API-Key` header.
- Compares against a `Set<String>` built from `app.security.api-keys` (comma-separated property, also overridable via `APP_SECURITY_API_KEYS` env var).
- Returns `401 UNAUTHORIZED` in the standard ApiError envelope on missing/invalid keys.
- Allow-listed endpoints (no API key required): `/health`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`.

The filter is registered via `FilterRegistrationBean` (not as a bean) to avoid double-registration via Spring Boot's automatic filter discovery. Order = 1 ensures it runs before any other servlet filter.

I deliberately did **not** pull in `spring-boot-starter-security` — the spec asked for an API key check, not an identity model. A 70-line filter is easier to audit than the security DSL for this use case.

## 13. How to run the system

```bash
# Start Postgres (and pgAdmin on port 8082)
docker compose up

# In a separate terminal, run the application
./mvnw spring-boot:run
```

The `docker-compose.yml` starts Postgres and pgAdmin. The application service is provided commented-out as a reference — run the app directly with Maven (or build the Docker image first with `./mvnw package` and `docker build -t inventory-app:0.1 .`). Liquibase migrations apply automatically on boot.

- **Swagger UI**: <http://localhost:8080/swagger-ui.html>
- **OpenAPI spec**: <http://localhost:8080/v3/api-docs>
- **Health**: <http://localhost:8080/health>
- **pgAdmin**: <http://localhost:8082> (email: `admin@example.com`, password: `adminpassword`)
- **Default API key** (override via `APP_SECURITY_API_KEYS`): `dev-api-key-001`

Quick smoke test:

```bash
# Reserve some stock
curl -s -X POST http://localhost:8080/api/v1/reservations \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: dev-api-key-001' \
  -d '{"orderId":"ORD-1001","items":[{"sku":"A100","quantity":5}]}'

# Look up the reservation
curl -s http://localhost:8080/api/v1/reservations/<id> \
  -H 'X-API-Key: dev-api-key-001'

# Check stock
curl -s http://localhost:8080/api/v1/inventory/A100 \
  -H 'X-API-Key: dev-api-key-001'
```

## 14. How to run the tests

```bash
# Unit tests only (fast, no Docker required)
./mvnw test

# Unit + integration tests (Testcontainers spins up Postgres in Docker)
./mvnw verify
```

Integration tests use Testcontainers with `withReuse(true)`, so a Postgres container is shared across runs once you've enabled reuse in `~/.testcontainers.properties` (`testcontainers.reuse.enable=true`). Otherwise a fresh container is started per test class — slower but self-contained.

## 15. Trade-offs I made

- **No Advanced tracks A or B.** I prioritised depth on the Core requirements over wiring NATS and Redis. The event boundary is designed so adding a NATS subscriber is one class.
- **In-process event dispatch.** Today, subscribers run synchronously inside the same transaction as the state change. That's a feature for the outbox subscriber (atomicity) and a small risk for the structured-log subscriber (a logger that hangs would slow the request). I judged the risk acceptable given how the logging library is used.
- **No outbox drainer included.** The outbox table is written transactionally; a separate process to drain `published_at IS NULL` rows to a real broker would be needed once Advanced track A is on. Implementation sketch is in §9.
- **API key set held in memory.** Adequate for the stated requirement ("valid keys can be configured via application properties or environment variables"). For real production I'd back this with a secret store and a hot-reload mechanism.
- **No rate limiting.** The spec didn't require it. With 10k rps as the target (see §16), it would be the next thing I added.
- **Pessimistic over optimistic locking** on the stock path. Trade-off discussed in §7 — pessimistic wins on contended SKUs at the cost of slightly more lock acquisition cost on uncontended paths.
- **Tests are good but not exhaustive.** I covered every business rule the spec called out, plus the four required concurrency / idempotency / pagination / security / OpenAPI / migration ITs. Edge cases I'd add given more time: very large `items[]` requests, expiry job racing the cancel API on the same row (`pg_advisory_xact_lock` would make this assertable in the test rather than only in the production code).

## 16. What would break at 10,000 reservation requests / minute

10k/min is ~167 rps sustained. The first thing to fail will depend on the **distribution across SKUs**:

- **Heavy skew on a single hot SKU** — pessimistic row-level locking on `inventory.sku` becomes the bottleneck. At ~167 rps for the same row, with each transaction holding the lock for the duration of the whole reservation (DB roundtrip + event write + outbox insert ≈ 5–15 ms), we'd serialize at roughly 65–200 successful reservations per second per hot SKU. Beyond that, transactions queue up; latency p99 climbs from a few ms into hundreds of ms; HikariCP pool exhausts; the API starts returning 500s as connection acquisition times out.

  **Fix**: shard the hot SKU into N "buckets" — `inventory_buckets(sku, bucket_id, stock)` — and pick a bucket per request (round-robin or hash of orderId). The state-of-the-art version of this is Pinot's "ledger-based inventory" or Stripe's "stock partitions" pattern. Behind the scenes you reconcile across buckets at low traffic.

- **Even spread across many SKUs** — Postgres handles 10k rps comfortably on a modest box, but the **outbox writes** become a write-amplification problem. Every reservation = 1 reservation row + 1 event row + N item rows + 1+N inventory updates. At 167 rps that's ~1k row writes/second, fine on commodity hardware but you'll start seeing checkpoint pressure. The connection pool (`maximum-pool-size: 20`) becomes the hard ceiling at roughly 20 × (1 / mean-tx-time) rps.

  **Fix**: tune Hikari pool to match (CPU cores × 2 + spindle count) per the Hikari guide, ~50 in a typical production setup; raise Postgres `max_wal_size` and `checkpoint_timeout`; introduce a write-side cache (Redis) so the read path doesn't compete with the write path for connections.

- **Expiry job** — if the PENDING backlog grows large (hot SKU running out of stock means lots of cancelled-due-to-bad-luck reservations don't enter the system, but anything that succeeds and times out does), the per-tick batch (`expiry-batch-size: 100`) may not keep up. The job will start running long, eventually overlapping with the next tick.

  **Fix**: make the batch size and frequency tunable; have the job loop until the batch is short (already implemented); add metrics so we alert on backlog age.

The single most likely first failure under realistic load is **HikariCP connection acquisition timeouts** when one or two SKUs are hot. The fix order I'd execute:

1. Add a `Micrometer` metric on `inventory.reserve_lock_wait_ms` per SKU and alert at p95 > 100 ms.
2. Bump the Hikari pool to ~50 once the metric tells us where the contention actually lives.
3. Shard the hot SKUs into buckets.
4. Add a Redis read-through cache for `GET /inventory/{sku}` so reads don't compete for connections (Advanced track B as designed).
5. Move the outbox publisher into a separate process so the API's write-tx is shorter.

---

## Appendix: Files of interest

- **State pattern**: `src/main/java/com/company/inventory/domain/state/`
- **Factory pattern**: `src/main/java/com/company/inventory/domain/factory/ReservationFactory.java`
- **Observer pattern**: `src/main/java/com/company/inventory/messaging/`
- **Concurrency**: `ReservationService.createReservation` and `findAndExpireReservation`, plus `InventoryRepository.findBySkuInOrderBySkuAsc` and `ReservationRepository.findExpired`.
- **Idempotency**: `ReservationService.createReservation` (the two-phase guard) + `ReservationController.create` (the recovery branch) + `003-create-reservations.sql` (the unique constraint).
- **Audit trail**: `log/StateTransitionLogger.java` + `logback-spring.xml`.
- **Liquibase changesets**: `src/main/resources/db/changelog/changes/`.
- **Concurrent integration tests**: `src/test/java/com/company/inventory/integration/ReservationConcurrencyIT.java`.