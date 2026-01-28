param(
  [switch]$StartServer
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$runsDir = Join-Path $repoRoot 'data\runs'

function Wait-Http([string]$url, [int]$timeoutSec = 180) {
  $sw = [Diagnostics.Stopwatch]::StartNew()
  while ($sw.Elapsed.TotalSeconds -lt $timeoutSec) {
    try {
      $r = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 5
      if ($r.StatusCode -eq 200) { return $true }
    } catch {}
    Start-Sleep -Milliseconds 500
  }
  return $false
}

function Stop-ServerIfListening8080() {
  try {
    $c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $c) { return }
    $owningPid = [int]$c.OwningProcess
    Stop-Process -Id $owningPid -Force -ErrorAction SilentlyContinue
  } catch {}
}

function Ensure-Recipe([hashtable]$recipe) {
  $json = $recipe | ConvertTo-Json -Depth 30
  $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/recipes' -Method Post -ContentType 'application/json' -Body $json -TimeoutSec 30
  if ($resp.StatusCode -ne 200) { throw "POST /api/recipes failed: HTTP $($resp.StatusCode)" }
}

function Post-Run([string]$recipeId) {
  $body = ('{{"recipeId":"{0}"}}' -f $recipeId)
  $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/runs' -Method Post -ContentType 'application/json' -Body $body -TimeoutSec 30
  return ($resp.Content | ConvertFrom-Json)
}

function Subscribe-Sse([string]$runId, [int]$maxTimeSec = 120) {
  $outDir = Join-Path $repoRoot 'target\verify'
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  $outFile = Join-Path $outDir ("sse_dds_{0}.txt" -f $runId)
  if (Test-Path $outFile) { Remove-Item $outFile -Force }
  cmd /c "curl.exe -sS -N --no-progress-meter --max-time $maxTimeSec http://localhost:8080/api/sse/runs/$runId 1> ""$outFile"" 2>&1" | Out-Null
  return @{ file = $outFile; exit = $LASTEXITCODE }
}

function Get-Utf8Json([string]$url) {
  $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 30
  $resp.RawContentStream.Position = 0
  $ms = New-Object IO.MemoryStream
  $resp.RawContentStream.CopyTo($ms)
  $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
  return $text | ConvertFrom-Json
}

$started = $false
$proc = $null

if ($StartServer) {
  Stop-ServerIfListening8080
  $logDir = Join-Path $repoRoot 'target\verify'
  New-Item -ItemType Directory -Force -Path $logDir | Out-Null
  $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
  $outLog = Join-Path $logDir ("boot_dds_{0}.out.log" -f $stamp)
  $errLog = Join-Path $logDir ("boot_dds_{0}.err.log" -f $stamp)
  $proc = Start-Process -FilePath 'mvn' -ArgumentList @('-q', 'spring-boot:run') -WorkingDirectory $repoRoot -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
  $started = $true
}

try {
  if (-not (Wait-Http 'http://localhost:8080/ui/devices' 240)) {
    throw 'Server not ready on http://localhost:8080'
  }

  $recipeId = 'RCP-DDS-DEMO'
  Ensure-Recipe @{
    recipeId = $recipeId
    name = 'DDS demo (MAIN only)'
    mainConfig = @{ txEnable = $true; ddsFreqHz = 10000000.0; referencePathDelayNs = 120.0; measurePathDelayNs = 180.0 }
    relayConfig = @{ txEnable = $true; ddsFreqHz = 123.0; referencePathDelayNs = 95.0; measurePathDelayNs = 130.0 }
    linkModel = @{ fixedLinkDelayNs = 800.0; driftPpm = 0.2; noiseStdNs = 0.5; basePhaseDeg = 15.0 }
    measurementPlan = @{ modes = @('LINK', 'MAIN_INTERNAL', 'RELAY_INTERNAL'); repeat = 2 }
    simulatorProfile = $null
  }

  $resp = Post-Run $recipeId
  if (-not $resp.success) { throw "start run failed: $($resp.code) $($resp.message)" }
  $runId = $resp.data.runId

  $sse = Subscribe-Sse $runId 120
  if ($sse.exit -ne 0) { throw "SSE did not close (curlExit=$($sse.exit))" }

  $infoPath = Join-Path (Join-Path $runsDir $runId) 'run_info.json'
  if (-not (Test-Path $infoPath)) { throw "run_info.json not found: $infoPath" }
  $runInfo = Get-Content -Raw -Encoding UTF8 $infoPath | ConvertFrom-Json

  $mainHz = $runInfo.mainAppliedConfig.ddsFreqHz
  $relayHz = $runInfo.relayAppliedConfig.ddsFreqHz

  if ($mainHz -ne 10000000.0) { throw "mainAppliedConfig.ddsFreqHz expected 10000000.0 got $mainHz" }
  if ($null -ne $relayHz) { throw "relayAppliedConfig.ddsFreqHz expected null got $relayHz" }

  $api = Get-Utf8Json "http://localhost:8080/api/runs/$runId"
  $apiMainHz = $api.data.mainAppliedConfig.ddsFreqHz
  $apiRelayHz = $api.data.relayAppliedConfig.ddsFreqHz
  if ($apiMainHz -ne 10000000.0) { throw "/api/runs/{runId} mainAppliedConfig.ddsFreqHz expected 10000000.0 got $apiMainHz" }
  if ($null -ne $apiRelayHz) { throw "/api/runs/{runId} relayAppliedConfig.ddsFreqHz expected null got $apiRelayHz" }

  Write-Output ("DDS PASS: runId={0} mainHz={1} relayHz={2}" -f $runId, $mainHz, $relayHz)
} finally {
  if ($started -and $proc) {
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
  }
  Stop-ServerIfListening8080
}
