# Security Policy

## Reporting

Do not open a public issue for a suspected vulnerability. Use GitHub private vulnerability reporting when enabled, or contact the repository owner privately. Include affected versions, reproduction steps, impact, and any suggested mitigation. Do not include real customer or employer data.

Expect an acknowledgement within five business days. Disclosure timing will be coordinated after triage and a fix or mitigation is available.

## Supported versions

Until the first release, only the current `main` branch receives security fixes.

## Security boundaries

- Replay execution must remain isolated from production side effects.
- PostgreSQL is canonical; Redis delivery is at least once and consumers must be idempotent.
- Trace payloads may be sensitive. This MVP has no authentication or redaction layer and must not be exposed to untrusted networks.
- Development database credentials are placeholders only. Deployments must set strong `DATABASE_USER` and `DATABASE_PASSWORD` values through their secret manager.
- Swagger UI and actuator metrics should be access-controlled or disabled outside trusted development environments.

See `docs/HARDENING_AUDIT.md` for accepted MVP risks.
