# ReplayForge MVP Architecture

## System shape

ReplayForge begins as a modular monolith plus a worker. The API owns trace ingestion and queries; the worker executes sample workflows and replays. Both share pure domain and replay libraries, PostgreSQL, and Redis Streams. The minimal web client calls the API and contains no replay logic.

```text
Minimal Web UI -> HTTP API -> PostgreSQL
                         |        ^
                         v        |
                    Redis Streams -> Worker
                                      |
                 domain state machine + replay engine
```

The event broker transports work. PostgreSQL is the source of truth for traces and replay artifacts. Replay correctness must not depend on broker timing.

## Neutral sample domain

### Event envelope

Every event has `event_id`, `trace_id`, `correlation_id`, optional `causation_id`, `sequence`, `event_type`, `schema_version`, `occurred_at`, `recorded_at`, `payload`, and `metadata`. Replay adds `replay_id`, `namespace`, `logical_time`, and provenance pointing to the original event or injected fault.

### Event types

- `ReservationCreated`
- `PaymentAuthorizationRequested`
- `PaymentAuthorized` / `PaymentAuthorizationFailed`
- `ReservationConfirmed`
- `ReservationCancellationRequested`
- `ReservationCancelled`
- `RefundRequested`
- `RefundIssued` / `RefundFailed`
- `CreatorPayoutRequested`
- `CreatorPayoutIssued` / `CreatorPayoutFailed`

The state contains reservation status, authorized and refunded amounts, payout status and amount, processed event IDs, and transition history. Money uses integer minor units plus ISO currency; floats are forbidden.

### Safety invariants

1. A cancelled and fully refunded reservation must never produce a creator payout.
2. Total refunded amount must never exceed the successfully authorized amount.
3. A reservation can be confirmed only after successful payment authorization.
4. At most one successful creator payout may exist per reservation, and its amount must not exceed the captured/authorized amount less refunds.
5. Processing the same event ID more than once must not change business state after its first accepted transition.
6. A terminally cancelled reservation cannot return to confirmed status.

Invariants are versioned functions over state plus transition context. Each result records invariant version, event position, pass/fail, and structured evidence.

## Proposed package layout

```text
apps/
  api/                 HTTP boundary and composition root
  worker/              capture/replay job consumers
  web/                 minimal trace and report UI
packages/
  contracts/           versioned schemas and API DTOs
  domain/              pure reservation state machine and invariants
  capture/             event envelope and append/checkpoint service
  replay/              scheduler, logical clock, seed, effect adapters
  faults/              one module per fault operator
  divergence/          transition comparison and state diff
  persistence/         PostgreSQL repositories and migrations
  broker/              Redis Streams publisher/consumer adapters
tests/
  fixtures/ integration/ e2e/
```

No package reads environment variables directly except application composition roots. Domain, fault, invariant, and comparison modules contain no database, network, system-clock, or global-random access.

## Data stores

PostgreSQL stores:

- `traces` and immutable `events`, ordered by trace-local sequence with a uniqueness constraint.
- `checkpoints` containing state, reducer version, and event position.
- `replay_plans` containing seed, start point, fault declarations, and component versions.
- `replay_runs`, transformed schedules, transition snapshots, invariant results, and divergence reports.

Use JSONB for versioned payloads and snapshots, while identifiers, ordering fields, status, and timestamps remain typed columns. A transactional outbox connects database writes to broker publication. Large artifact/object storage is deferred until measured need.

## Queue choice

Redis Streams carries workflow and replay jobs locally and in the MVP deployment. Consumer groups provide explicit acknowledgements, pending-entry recovery, and a lightweight local footprint. Replay streams and consumer groups are scoped by replay namespace.

The application assumes at-least-once delivery and enforces idempotency using event/job IDs. Logical trace order comes from persisted sequence numbers, not arrival order. The broker is replaceable through narrow publisher and consumer ports.

## API surface

```text
POST /api/v1/sample-runs                 start the neutral workflow
POST /api/v1/traces/import               import a versioned trace document
GET  /api/v1/traces                      list traces
GET  /api/v1/traces/{traceId}            trace, timeline, checkpoints, final state
POST /api/v1/traces/{traceId}/replays    create a replay plan and enqueue execution
GET  /api/v1/replays/{replayId}          status, plan, and execution metadata
GET  /api/v1/replays/{replayId}/report   divergence and final-state diff
GET  /api/v1/replays/{replayId}/export   downloadable versioned JSON report
```

Replay creation accepts a checkpoint (or start), integer seed, and ordered fault declarations. Mutating requests accept an idempotency key. Errors use a stable machine-readable envelope.

## Deterministic execution model

1. Load the original events and selected checkpoint in persisted sequence order.
2. Validate schema and reducer compatibility before execution.
3. Derive all random decisions and generated identifiers from the stored seed.
4. Compile faults into an immutable schedule, recording provenance for every change.
5. Advance a logical clock; never consult wall-clock time inside replayed logic.
6. Route external effects to deterministic recorded-response or scripted-failure adapters.
7. Reduce one scheduled item at a time, persist transition state, and evaluate invariants.
8. Compare normalized original and replay transitions to locate the first semantic divergence.

Concurrency is represented as an explicit schedule. Delays change logical delivery position/time and do not sleep. Reports distinguish a fault application point, first state divergence, and first invariant failure because these may be different events.

## Fault model

Faults are declarative, ordered, versioned transformations targeting event identity/type/position:

- duplicate an event a specified number of times;
- delay it by logical duration or delivery positions;
- drop it;
- reorder selected events explicitly;
- apply a typed malformed-payload patch;
- return a scripted timeout from an effect adapter;
- fail a worker before or after state persistence/acknowledgement.

Each applied transformation is retained in the replay schedule. Invalid or ambiguous targets fail plan validation rather than silently doing nothing.

## Divergence reporting

States are normalized using versioned rules before comparison. The report contains trace/replay IDs, seed, checkpoint, versions, fault plan and realized schedule, first divergent transition with before/after states, invariant evidence, and a JSON Patch-style final-state diff. Operational metadata such as replay IDs and wall-clock recording timestamps is excluded from semantic comparison but remains available as evidence.

## Testing strategy

- **Unit:** reducers, invariants, schedule compilation, every fault operator, normalization, and diffing.
- **Property-based:** duplicate idempotency, refund/payout bounds, stable ordering, and serialization round trips.
- **Golden fixtures:** versioned traces and expected schedules/reports reviewed as product examples.
- **Determinism:** repeat identical seed/plan runs and compare normalized artifacts byte-for-byte.
- **Checkpoint equivalence:** full replay and checkpoint replay must converge when given equivalent inputs.
- **Integration:** PostgreSQL migrations/repositories, outbox behavior, Redis pending-entry recovery, and crash boundaries.
- **End-to-end:** the required successful-original then faulted-replay demonstration through API and UI.

Benchmarks will be executable scripts for capture throughput, replay throughput, and report size; documentation will contain placeholders until measurements are run on a stated machine and dataset.

## Local development workflow

The initial workflow uses Docker Compose for PostgreSQL and Redis, Java 21, Spring Boot 3, Maven, and Make targets for setup, run, unit tests, integration tests, and local cleanup. Later milestones will add end-to-end, seed-demo, and benchmark tasks when those capabilities exist.

## Security and isolation

- Replay namespaces prevent subjects, idempotency keys, and persisted state from colliding with original runs.
- Effect adapters deny real outbound side effects by default during replay.
- Imported traces are schema-validated and size-limited; malformed payload injection occurs only after import validation inside an isolated run.
- Sensitive payload fields require configurable redaction before persistence or export.

## ADR-001: Redis Streams as the MVP event broker

- **Status:** Superseded and accepted for implementation (Redis Streams replaces the earlier NATS planning choice)
- **Context:** ReplayForge needs at-least-once delivery, observable redelivery, durable consumers, and easy local operation. Broker arrival order cannot be the replay truth.
- **Decision:** Use Redis Streams for workflow commands/events and replay jobs, behind publisher/consumer ports. Use PostgreSQL sequence numbers and a transactional outbox for authoritative ordering and reliable publication.
- **Consequences:** Local setup stays small and pending-entry recovery scenarios are easy to demonstrate. The application must implement idempotent consumers, explicit acknowledgement/reclaim behavior, and outbox dispatch. Kafka-scale retention and partition tooling are deferred; broker replacement remains possible but not free.
- **Rejected alternatives:** In-memory queues cannot exercise crash/redelivery behavior. PostgreSQL-only polling obscures broker failure modes. Kafka adds operational weight not justified by the MVP.

## ADR-002: Pure, single-threaded deterministic replay core

- **Status:** Accepted for MVP planning
- **Context:** Reproducing ordering and retry failures requires the same input, checkpoint, seed, and component versions to yield the same transitions regardless of machine timing.
- **Decision:** Execute replay as a pure, single-threaded reduction over a precompiled immutable schedule. Inject a seeded random source, logical clock, deterministic ID generator, and scripted effect adapters. Persist the plan, realized schedule, schema/reducer/invariant versions, and transition outputs.
- **Consequences:** Results are reproducible and divergence is attributable event-by-event. Real concurrency must be modeled as schedule order, and nondeterministic production dependencies require recorded or scripted adapters. Parallelism may occur across replay runs, never within one run's reduction.
- **Rejected alternatives:** Replaying against wall-clock time or live dependencies is not reproducible. Concurrent worker execution introduces scheduler nondeterminism. Recording only final state cannot locate causal divergence.
