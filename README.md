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
```

Start a full deterministic replay with checkpoint `0`, or set the checkpoint to rebuild baseline state and emit only source events after that sequence:

```json
{"checkpoint":0,"seed":42,"clockMode":"FIXED_EPOCH"}
```

Replay execution uses virtual time. Operational run timestamps use the system clock, but replay ordering and replay event timestamps never use sleeps or wall-clock progression.

Configuration defaults support the Compose services. Copy `.env.example` and export its values only when overrides are needed; Spring does not automatically load `.env` files.

The sample workflow uses Redis consumer groups named `reservation`, `payment`, `refund`, and `payout`. Failed deliveries are retried up to the configured bound and then written to `replayforge:workflow:dlq`. PostgreSQL remains canonical: the event stream, consumer receipts, projection, and transactional outbox survive consumer restarts.

## Roadmap

1. Complete event capture and the versioned reservation workload.
2. Extend deterministic replay with controlled fault schedules.
3. Add duplicate, delay, drop, reorder, malformed-payload, timeout, and worker-failure faults.
4. Evaluate versioned safety invariants at every transition.
5. Report the first divergence and final-state diff through a minimal UI and export.

See `PLAN.md` and `ARCHITECTURE.md` for scope and design decisions.
