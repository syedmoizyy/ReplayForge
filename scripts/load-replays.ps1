[CmdletBinding()]
param(
    [Parameter(Mandatory)] [Guid] $CorrelationId,
    [ValidateRange(1, 1000)] [int] $Requests = 50,
    [ValidateRange(1, 128)] [int] $Concurrency = 16,
    [ValidatePattern('^https?://')] [string] $BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
$endpoint = "$($BaseUrl.TrimEnd('/'))/api/v1/traces/$CorrelationId/replays"
$started = [DateTimeOffset]::UtcNow
$responses = 1..$Requests | ForEach-Object -ThrottleLimit $Concurrency -Parallel {
    $body = @{ checkpoint = 0; seed = $_; clockMode = 'FIXED_EPOCH' } | ConvertTo-Json -Compress
    try {
        $response = Invoke-WebRequest -Method Post -Uri $using:endpoint -ContentType 'application/json' -Body $body
        [pscustomobject]@{ status = [int]$response.StatusCode; retryAfter = $null }
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $retryAfter = if ($_.Exception.Response -and $_.Exception.Response.Headers.RetryAfter) {
            $_.Exception.Response.Headers.RetryAfter.ToString()
        } else { $null }
        [pscustomobject]@{ status = $status; retryAfter = $retryAfter }
    }
}
$finished = [DateTimeOffset]::UtcNow
$report = [ordered]@{
    generatedAt = $finished.ToString('O')
    target = $endpoint
    requests = $Requests
    concurrency = $Concurrency
    elapsedMs = [math]::Round(($finished - $started).TotalMilliseconds, 3)
    statuses = @($responses | Group-Object status | ForEach-Object { [ordered]@{ status = [int]$_.Name; count = $_.Count } })
    retryAfterValues = @($responses.retryAfter | Where-Object { $_ } | Sort-Object -Unique)
}
$outputDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'target\load'
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$output = Join-Path $outputDirectory 'concurrent-replays.json'
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $output -Encoding utf8
Write-Host "Load report: $output"
