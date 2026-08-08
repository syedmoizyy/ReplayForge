# Hardening audit

## Addressed

- **Capacity:** replay workers and queue are bounded and configurable. Rejected requests become failed run records and return `429 REPLAY_CAPACITY_EXHAUSTED` with `Retry-After`.
- **Input bounds:** source traces exceeding `REPLAY_MAX_SOURCE_EVENTS` are rejected before scheduling.
- **Transactions:** workflow event, projection, receipt, and outbox changes remain in Spring transactions. Replay artifact completion is atomic.
- **Concurrency:** aggregate appends use transaction-scoped PostgreSQL advisory locks and unique constraints; projection updates use optimistic sequence checks.
- **Thread safety:** replay-run state is method-local; the shared engine's concurrent deterministic output is covered by a 32-run virtual-thread test. Metrics use thread-safe Micrometer instruments.
- **Indexes:** aggregate/trace ordering, pending outbox work, recent replay lists, and active replay status have supporting indexes.
- **Logging:** replay queued, started, completed, failed, and capacity-rejected transitions use structured identifiers without payloads or secrets.
- **Validation:** configuration bounds prevent unbounded worker, queue, trace, retry, and backoff settings.

## Accepted MVP risks

- No authentication, authorization, payload redaction, or retention enforcement exists. Bind the service to trusted development networks only.
- Redis publication and PostgreSQL outbox acknowledgement cannot be atomic. Duplicate publication is possible by design and is handled through consumer receipts/idempotency.
- Consumer retry backoff blocks its polling thread. Attempts and backoff are bounded; a delayed scheduler is deferred until broker throughput demonstrates need.
- A process crash can leave a replay in `QUEUED` or `RUNNING`. Startup reconciliation/leases are deferred and should precede production deployment.
- Replay rejection creates then marks a run failed, preserving auditability at the cost of a failed record per rejected request.
- The default Compose credentials are development-only. `.env` is ignored; deployment secrets must come from an external secret manager.

## Quality tools

`mvn -Pquality verify` runs SpotBugs and OWASP Dependency-Check. Dependency-Check can fail when its advisory feed is unavailable; that is an environmental failure, not a suppressible false positive. Findings require review before suppression. Generated reports live under `target/` and are not committed.

`npm audit --omit=dev` checks runtime web dependencies. The Vite client currently has no production API client dependency beyond React; development-tool findings still require review before release.
