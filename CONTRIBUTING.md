# Contributing to ReplayForge

ReplayForge is a deterministic failure-reproduction tool, not a general observability dashboard. Changes should preserve captured trace inspection, isolated replay, controlled faults, invariant evidence, and causal divergence reporting.

## Development workflow

1. Read `PLAN.md`, `ARCHITECTURE.md`, and the ADRs embedded in `ARCHITECTURE.md`.
2. Create small modules with pure replay/domain logic and narrow infrastructure adapters.
3. Run `mvn test`, `mvn verify`, and `cd web && npm run build && npm run test:e2e`.
4. Run `mvn -Pquality verify` for SpotBugs and dependency analysis.
5. Describe transaction, retry, determinism, and compatibility effects in the pull request.

Do not include real production traces, credentials, private company information, or unmeasured performance claims. Generated fixtures must be labeled. Security reports should follow `SECURITY.md`, not public issues.

## Test selection

- Unit tests isolate pure rules and failure handling.
- Generated/property tests probe invariants across many deterministic inputs.
- Integration tests justify database, Redis, transaction, or migration behavior.
- Contract tests pin externally observable HTTP behavior.
- End-to-end tests cover only the core user journey.

Format commit subjects as an imperative sentence under 72 characters.
