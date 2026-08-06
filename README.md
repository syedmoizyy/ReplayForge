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

When running, liveness and readiness are exposed at:

```text
GET http://localhost:8080/actuator/health/liveness
GET http://localhost:8080/actuator/health/readiness
```

Configuration defaults support the Compose services. Copy `.env.example` and export its values only when overrides are needed; Spring does not automatically load `.env` files.

## Roadmap

1. Complete event capture and the versioned reservation workload.
2. Add deterministic replay from the beginning or a checkpoint.
3. Add duplicate, delay, drop, reorder, malformed-payload, timeout, and worker-failure faults.
4. Evaluate versioned safety invariants at every transition.
5. Report the first divergence and final-state diff through a minimal UI and export.

See `PLAN.md` and `ARCHITECTURE.md` for scope and design decisions.
