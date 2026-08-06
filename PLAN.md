# ReplayForge MVP Plan

## Current-state audit

### What exists

- An empty Git repository on `main` with no commits or project files.
- A defined product direction: reproduce event-driven workflow failures through deterministic replay and causal comparison.

### What is missing

- Executable application code, package structure, dependency manifests, and local infrastructure.
- Event and trace schemas, persistence, broker integration, replay engine, fault model, invariant engine, and reporting.
- API and minimal demonstration UI.
- Unit, integration, determinism, and end-to-end tests.

### Likely risks

- Nondeterminism leaking in through wall-clock time, random values, concurrency, external calls, or unstable serialization.
- Confusing broker delivery behavior with the logical event order stored in a trace.
- Replay side effects escaping the isolated replay namespace.
- Fault combinations producing ambiguous results unless every transformation is recorded.
- Domain-specific assumptions becoming coupled to the replay engine.
- A UI-heavy implementation obscuring the backend system before its correctness is proven.

## Locked MVP scope

The MVP consists of five capabilities:

1. **Event capture** — record correlated workflow events, inputs, metadata, checkpoints, and resulting state.
2. **Deterministic replay** — reproduce a trace from its beginning or a checkpoint with a fixed seed, logical clock, and isolated namespace.
3. **Fault injection** — apply declared duplicate, delay, drop, reorder, malformed-payload, timeout, or worker-failure transformations.
4. **Invariant checking** — evaluate safety rules after each transition and at replay completion.
5. **Divergence reporting** — identify the first divergent event and show event, invariant, and final-state differences.

Out of scope for the MVP: production traffic interception, arbitrary-language worker execution, distributed replay across hosts, hosted multi-tenancy, generalized observability dashboards, and performance claims without measured evidence.

## Milestones

### M1 — Domain contracts and executable state machine

- Define versioned trace, event, checkpoint, replay-plan, fault, invariant-result, and divergence-report schemas.
- Implement the neutral reservation state machine and deterministic serialization.
- Cover successful reservation/payment/confirmation and cancellation/refund paths with unit tests.

**Exit:** a fixture trace can be reduced to an expected final state without infrastructure.

### M2 — Event capture and storage

- Add append-only capture with correlation and causation identifiers.
- Persist traces, ordered event envelopes, checkpoints, and state snapshots in PostgreSQL.
- Add import and query endpoints.

**Exit:** a sample workflow can be run or imported and inspected as a complete trace.

### M3 — Deterministic replay

- Add seeded IDs/randomness, a logical clock, stable ordering, and external-effect adapters.
- Replay from the beginning or a checkpoint in a unique namespace.
- Prove repeated runs of the same trace and seed produce byte-identical normalized results.

**Exit:** deterministic replay passes repeated-run and checkpoint-equivalence tests.

### M4 — Fault injection and invariants

- Compile replay plans into an immutable event schedule.
- Implement the seven MVP fault types.
- Evaluate invariants after every transition and retain evidence.

**Exit:** duplicate and reordered-event scenarios trigger a documented safety violation.

### M5 — Divergence report and demo

- Compare original and replay transition-by-transition.
- Report the first divergence, violated invariant, applied fault, and final JSON state diff.
- Add a minimal trace/replay/report UI and JSON report export.

**Exit:** the demo shows a successful original workflow, an injected duplicate or reorder, an invariant violation, its exact divergence point, and the final-state diff.

### M6 — Hardening and release readiness

- Add property-based ordering/idempotency tests, broker/database integration tests, and end-to-end demo tests.
- Document recovery behavior, schema compatibility, security boundaries, and local setup.
- Add benchmark scripts with unfilled expectations; publish only locally observed results.

**Exit:** a clean checkout can run the documented workflow and test suite locally.

## Delivery principles

- Keep domain logic pure and infrastructure behind narrow ports.
- Store inputs and decisions needed for replay; never infer missing historical timing.
- Make replay plans and outputs immutable and auditable.
- Prefer one deployable backend with small modules before splitting services.
- Require tests for every new fault operator and invariant.

