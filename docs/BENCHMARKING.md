# Replay benchmark

Run:

```powershell
pwsh -File scripts/benchmark-replay.ps1
```

The harness warms up the deterministic engine, then measures median latency, p95 latency, and median throughput for 10, 100, 1,000, and 10,000-event generated traces. It records OS, architecture, Java version, processor count, maximum heap, seed, warmups, and iterations in `target/benchmark/replay-benchmark.json`.

This is a local microbenchmark, not a production capacity claim. Do not copy results into product documentation without retaining the generated environment metadata. For concurrent API admission behavior, start the application and run:

```powershell
pwsh -File scripts/load-replays.ps1 -CorrelationId <captured-correlation-id> -Requests 50 -Concurrency 16
```

That report is written to `target/load/concurrent-replays.json` and should show accepted requests plus explicit HTTP 429 responses when configured capacity is exceeded. Exact counts depend on trace size and machine speed.
