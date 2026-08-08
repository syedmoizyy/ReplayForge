# ReplayForge

ReplayForge reproduces difficult failures in event-driven backend workflows through captured traces, deterministic replay, controlled fault injection, invariant checks, and causal divergence reports.

## Prerequisites

- Java 21
- Maven 3.9 or newer
- Docker with Docker Compose v2
- GNU Make (optional; the underlying commands are shown below)

## Verified project commands

The repository defines the following setup and build commands. They require the prerequisites above; this initial scaffold was created on a host that did not have Java 21, Maven, or Docker, so runtime success is not yet claimed.

```sh
docker compose up -d --wait
mvn spring-boot:run
mvn test
mvn verify
mvn -Pquality verify
```

With the application running, seed one normal payout workflow and one cancellation/refund workflow:

```powershell
pwsh -File scripts/seed-workflows.ps1
```

When running, liveness and readiness are exposed at:

```text
GET http://localhost:8080/actuator/health/liveness
GET http://localhost:8080/actuator/health/readiness
POST http://localhost:8080/api/v1/sample-workflows
POST http://localhost:8080/api/v1/sample-workflows/{reservationId}/cancel
GET http://localhost:8080/api/v1/sample-workflows/{reservationId}
POST http://localhost:8080/api/v1/traces/{correlationId}/replays
GET http://localhost:8080/api/v1/replays/{replayId}
GET http://localhost:8080/api/v1/replays/{replayId}/trace
GET http://localhost:8080/api/v1/replays/{replayId}/state
GET http://localhost:8080/api/v1/replays/{replayId}/report
```

`/api/v1/workflows` is the canonical alias for the sample-workflow endpoints above; the older route remains available for compatibility.

The finalized REST surface also includes trace/replay lists, scenario validation, violation evidence, and JSON/Markdown report exports. Interactive OpenAPI documentation is served at `http://localhost:8080/docs` and the machine-readable document at `/v3/api-docs`.

## Minimal web dashboard

The Vite client intentionally has four screens only: traces, scenario runner, replay detail, and divergence report. Its bundled fixture is generated demonstration data and is labeled as such; it does not represent a benchmark.

```sh
cd web
npm install
npm run dev
npm run build
npm run test:e2e
```

During development, Vite proxies `/api` to the Spring application on port 8080. The single Playwright test walks the seeded happy path from trace inspection to divergence evidence.

The fixture UI exposes its error/retry state at `/?state=error`; filtering the seeded trace to a non-match demonstrates the empty state. Displayed duration and throughput values are generated fixture data, not benchmark measurements.

Replay execution is bounded by configurable worker, queue, and source-event limits. Capacity exhaustion returns HTTP `429` with a `Retry-After` header instead of growing resource use without bound. See `docs/HARDENING_AUDIT.md` for the audit and accepted MVP risks.

Local replay benchmarks and concurrent admission checks are documented in `docs/BENCHMARKING.md`. Reports are generated beneath `target/` and contain only measurements produced by that run.

Start a full deterministic replay with checkpoint `0`, or set the checkpoint to rebuild baseline state and emit only source events after that sequence:

```json
{"checkpoint":0,"seed":42,"clockMode":"FIXED_EPOCH"}
```

Replay execution uses virtual time. Operational run timestamps use the system clock, but replay ordering and replay event timestamps never use sleeps or wall-clock progression.

Each replay evaluates the versioned invariant registry after every transition and once at completion. Hard failures and warnings retain related event IDs, the state snapshot, expected and actual conditions, severity, and event position. The standard registry checks payout-after-refund, at-most-once financial effects, workflow transitions, terminal refunds after cancellation, and monotonic sequence ordering.

The report endpoint returns stable machine-readable JSON and concise Markdown comparing baseline and replay event order, event type, payload fields, financial side effects, and final aggregate state. Replay runs emit an OpenTelemetry `replay.execute` span with correlation and seed attributes, plus Micrometer metrics for processed events, injected faults, violations, duration, and throughput under the `replayforge.replay.*` prefix.

## Fault scenarios

Versioned JSON examples live in `examples/fault-scenarios`. A scenario declares a deterministic seed, ordered faults, selectors (event type, aggregate ID, sequence range, attempt range, and seeded probability), and mandatory execution limits. Compilation produces an immutable logical schedule plus an audit decision for every applied or skipped selector evaluation. Worker crashes and dependency timeouts are explicit directives for isolated replay adapters; they never invoke production side effects.

Supported fault types are `DUPLICATE`, `DROP`, `DELAY`, `REORDER`, `WORKER_CRASH`, `RETRY_STORM`, `DEPENDENCY_TIMEOUT`, and `MALFORMED_PAYLOAD`. Delays advance logical delivery time rather than sleeping. Limits bound duplicates per match, delay duration, retries per match, source events, and the final compiled schedule size.

Configuration defaults support the Compose services. Copy `.env.example` and export its values only when overrides are needed; Spring does not automatically load `.env` files.

The sample workflow uses Redis consumer groups named `reservation`, `payment`, `refund`, and `payout`. Failed deliveries are retried up to the configured bound and then written to `replayforge:workflow:dlq`. PostgreSQL remains canonical: the event stream, consumer receipts, projection, and transactional outbox survive consumer restarts.

## Project policies

See `CONTRIBUTING.md`, `SECURITY.md`, `CODE_OF_CONDUCT.md`, `LICENSE`, and the concise `ROADMAP.md`.

## Roadmap

1. Complete event capture and the versioned reservation workload.
2. Extend deterministic replay with controlled fault schedules.
3. Add duplicate, delay, drop, reorder, malformed-payload, timeout, and worker-failure faults.
4. Evaluate versioned safety invariants at every transition.
5. Report the first divergence and final-state diff through a minimal UI and export.

See `PLAN.md` and `ARCHITECTURE.md` for scope and design decisions.
