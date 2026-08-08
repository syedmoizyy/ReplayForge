[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    mvn -Dreplayforge.benchmark=true -Dtest=ReplayBenchmark test
    $report = Join-Path $repoRoot 'target\benchmark\replay-benchmark.json'
    if (-not (Test-Path -LiteralPath $report)) { throw "Benchmark completed without producing $report" }
    Write-Host "Benchmark report: $report"
} finally {
    Pop-Location
}
