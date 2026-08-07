param([string]$BaseUrl = "http://localhost:8080")

$normal = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/sample-workflows" `
  -Headers @{"Idempotency-Key"="seed-normal"} -ContentType "application/json" `
  -Body '{"depositAmount":2500,"currency":"USD","autoPayout":true}'

$cancelled = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/sample-workflows" `
  -Headers @{"Idempotency-Key"="seed-cancel"} -ContentType "application/json" `
  -Body '{"depositAmount":4000,"currency":"USD","autoPayout":false}'

for ($attempt = 0; $attempt -lt 40; $attempt++) {
  $state = Invoke-RestMethod -Uri "$BaseUrl/api/v1/sample-workflows/$($cancelled.aggregateId)"
  if ($state.status -eq "CONFIRMED") { break }
  Start-Sleep -Milliseconds 250
}
if ($state.status -ne "CONFIRMED") { throw "Cancellation seed did not reach CONFIRMED" }

Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/v1/sample-workflows/$($cancelled.aggregateId)/cancel" `
  -Headers @{"Idempotency-Key"="seed-cancel-confirmed"} | Out-Null

[pscustomobject]@{normalReservationId=$normal.aggregateId; cancellationReservationId=$cancelled.aggregateId}
