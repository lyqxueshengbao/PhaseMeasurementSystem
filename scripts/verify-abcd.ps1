param(
  [switch]$StartServer
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$dataRuns = Join-Path $repoRoot 'data\runs'

function Ensure-Recipe([hashtable]$recipe) {
  $json = $recipe | ConvertTo-Json -Depth 30
  $resp = Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/api/recipes' -Method Post -ContentType 'application/json' -Body $json -TimeoutSec 30
  if ($resp.StatusCode -ne 200) { throw "POST /api/recipes failed: HTTP $($resp.StatusCode)" }
}

function Copy-Hashtable([hashtable]$h) {
  $copy = @{}
  foreach ($k in $h.Keys) { $copy[$k] = $h[$k] }
  return $copy
}

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

function Get-Utf8Json([string]$url) {
  $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 30
  $resp.RawContentStream.Position = 0
  $ms = New-Object IO.MemoryStream
  $resp.RawContentStream.CopyTo($ms)
  $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
  return $text | ConvertFrom-Json
}

function Post-Utf8Json([string]$url, [string]$jsonBody) {
  $resp = Invoke-WebRequest -UseBasicParsing -Uri $url -Method Post -ContentType 'application/json' -Body $jsonBody -TimeoutSec 30
  $resp.RawContentStream.Position = 0
  $ms = New-Object IO.MemoryStream
  $resp.RawContentStream.CopyTo($ms)
  $text = [Text.Encoding]::UTF8.GetString($ms.ToArray())
  return $text | ConvertFrom-Json
}

function Start-Run([string]$recipeId) {
  $body = ('{{"recipeId":"{0}"}}' -f $recipeId)
  return Post-Utf8Json 'http://localhost:8080/api/runs' $body
}

function Subscribe-Sse([string]$runId, [int]$maxTimeSec = 180) {
  $outDir = Join-Path $repoRoot 'target\verify'
  New-Item -ItemType Directory -Force -Path $outDir | Out-Null
  $outFile = Join-Path $outDir ("sse_{0}.txt" -f $runId)
  if (Test-Path $outFile) { Remove-Item $outFile -Force }

  cmd /c "curl.exe -sS -N --no-progress-meter --max-time $maxTimeSec http://localhost:8080/api/sse/runs/$runId 1> ""$outFile"" 2>&1" | Out-Null
  return @{ file = $outFile; exit = $LASTEXITCODE }
}

function Parse-SseSeq([string]$path) {
  $seqs = New-Object System.Collections.Generic.List[long]
  $lastType = $null
  $pendingData = $false
  foreach ($lineRaw in Get-Content -LiteralPath $path -ErrorAction Stop) {
    $line = $lineRaw.Trim()
    if ($line -like 'data:*') {
      $json = $line.Substring(5).Trim()
      if ($json.StartsWith('{')) {
        try {
          $o = $json | ConvertFrom-Json
          if ($o.seq) { $seqs.Add([long]$o.seq) | Out-Null }
          $lastType = $o.type
        } catch {}
        $pendingData = $false
      } else {
        $pendingData = $true
      }
    } elseif ($pendingData -and $line.StartsWith('{')) {
      try {
        $o = $line | ConvertFrom-Json
        if ($o.seq) { $seqs.Add([long]$o.seq) | Out-Null }
        $lastType = $o.type
      } catch {}
      $pendingData = $false
    }
  }
  $mono = $true
  for ($i = 1; $i -lt $seqs.Count; $i++) {
    if ($seqs[$i] -le $seqs[$i - 1]) { $mono = $false; break }
  }
  return @{ count = $seqs.Count; monotonic = $mono; lastType = $lastType }
}

function Assert([bool]$cond, [string]$msg) {
  if (-not $cond) { throw $msg }
}

function Check-Run([string]$name, [string]$recipeId, [string]$expectLastType, [bool]$expectAtmos, [string]$expectFailCode, [string]$expectFailStep) {
  Write-Output ("[{0}] start recipeId={1}" -f $name, $recipeId)
  $resp = Start-Run $recipeId
  $runId = $resp.data.runId
  Write-Output ("[{0}] runId={1}" -f $name, $runId)

  $sse = Subscribe-Sse $runId 180
  Assert ($sse.exit -eq 0) ("[{0}] SSE did not close (curlExit={1})" -f $name, $sse.exit)
  $seq = Parse-SseSeq $sse.file
  Assert ($seq.monotonic) ("[{0}] SSE seq not monotonic" -f $name)
  Assert ($seq.lastType -eq $expectLastType) ("[{0}] SSE lastType expected={1} got={2}" -f $name, $expectLastType, $seq.lastType)

  $dir = Join-Path $dataRuns $runId
  $need = @('recipe.json', 'device_info.json', 'run_info.json', 'logs.ndjson', 'measurement_result.json')
  foreach ($f in $need) { Assert (Test-Path (Join-Path $dir $f)) ("[{0}] missing file {1}" -f $name, $f) }

  $hasAtmos = Test-Path (Join-Path $dir 'atmospheric_delay.json')
  $hasErr = Test-Path (Join-Path $dir 'error.json')
  if ($expectAtmos) {
    Assert $hasAtmos ("[{0}] missing atmospheric_delay.json" -f $name)
    Assert (-not $hasErr) ("[{0}] should not have error.json" -f $name)
  } else {
    Assert $hasErr ("[{0}] missing error.json" -f $name)
    $err = Get-Content -Raw -Encoding UTF8 (Join-Path $dir 'error.json') | ConvertFrom-Json
    Assert ($err.errorCode -eq $expectFailCode) ("[{0}] error.json.errorCode expected={1} got={2}" -f $name, $expectFailCode, $err.errorCode)
    Assert ($err.step -eq $expectFailStep) ("[{0}] error.json.step expected={1} got={2}" -f $name, $expectFailStep, $err.step)
  }

  $m = Get-Utf8Json "http://localhost:8080/api/runs/$runId/measurement_result"
  Assert ($m.success -eq $true -and $m.code -eq 'OK') ("[{0}] /measurement_result must be success=true code=OK" -f $name)

  $a = Get-Utf8Json "http://localhost:8080/api/runs/$runId/atmospheric_delay"
  if ($expectAtmos) {
    Assert ($a.success -eq $true -and $a.code -eq 'OK' -and $a.data.status -eq 'SUCCEEDED') ("[{0}] /atmospheric_delay must be success=true code=OK status=SUCCEEDED" -f $name)
  } else {
    Assert ($a.success -eq $false -and $a.code -eq $expectFailCode) ("[{0}] /atmospheric_delay must be success=false code={1}" -f $name, $expectFailCode)
    $err = Get-Content -Raw -Encoding UTF8 (Join-Path $dir 'error.json') | ConvertFrom-Json
    Assert ($a.data.errorCode -eq $err.errorCode -and $a.data.step -eq $err.step -and $a.data.message -eq $err.message -and $a.data.ts -eq $err.ts) ("[{0}] /atmospheric_delay data != error.json" -f $name)
  }

  Write-Output ("[{0}] PASS" -f $name)
  return $runId
}

function Stop-ServerIfListening8080() {
  try {
    $c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $c) { return }
    $owningPid = [int]$c.OwningProcess
    Stop-Process -Id $owningPid -Force -ErrorAction SilentlyContinue
  } catch {}
}

$started = $false
$proc = $null

if ($StartServer) {
  Stop-ServerIfListening8080
  $logDir = Join-Path $repoRoot 'target\verify'
  New-Item -ItemType Directory -Force -Path $logDir | Out-Null
  $stamp = Get-Date -Format 'yyyyMMdd_HHmmss'
  $outLog = Join-Path $logDir ("boot_{0}.out.log" -f $stamp)
  $errLog = Join-Path $logDir ("boot_{0}.err.log" -f $stamp)

  $proc = Start-Process -FilePath 'mvn' -ArgumentList @('-q', 'spring-boot:run') -WorkingDirectory $repoRoot -RedirectStandardOutput $outLog -RedirectStandardError $errLog -PassThru
  $started = $true
}

try {
  Assert (Wait-Http 'http://localhost:8080/ui/devices' 240) 'Server not ready on http://localhost:8080'

  # Heartbeat smoke: /api/devices should include rttMs for MAIN/RELAY (nullable, but field must exist)
  $dev = Get-Utf8Json 'http://localhost:8080/api/devices'
  Assert ($dev.success -eq $true -and $dev.code -eq 'OK') '/api/devices must be success=true code=OK'
  Assert (($dev.data.MAIN.PSObject.Properties.Name -contains 'rttMs') -and ($dev.data.RELAY.PSObject.Properties.Name -contains 'rttMs')) 'DeviceStatus must include rttMs'
  Write-Output ("[PING] MAIN.rttMs={0} RELAY.rttMs={1}" -f $dev.data.MAIN.rttMs, $dev.data.RELAY.rttMs)

  # Create/overwrite 4 recipes (DEFAULT/B/C/D) for deterministic acceptance checks.
  $base = @{
    mainConfig = @{ txEnable = $true; referencePathDelayNs = 120.0; measurePathDelayNs = 180.0 }
    relayConfig = @{ txEnable = $true; referencePathDelayNs = 95.0; measurePathDelayNs = 130.0 }
    linkModel = @{ fixedLinkDelayNs = 800.0; driftPpm = 0.2; noiseStdNs = 0.5; basePhaseDeg = 15.0 }
    measurementPlan = @{ modes = @('LINK', 'MAIN_INTERNAL', 'RELAY_INTERNAL'); repeat = 8 }
    simulatorProfile = @{
      applyDelayMs = 300
      lockTimeMs = 1200
      measurementTimeMs = 800
      faultType = 'NONE'
      randomLostLockProbability = 0.5
    }
  }

  $r1 = Copy-Hashtable $base
  $r1.recipeId = 'RCP-DEFAULT'
  $r1.name = '默认联调配方'
  Ensure-Recipe $r1

  $r2 = Copy-Hashtable $base
  $r2.recipeId = 'RCP-LOCK-TIMEOUT'
  $r2.name = '锁定超时失败'
  $r2.simulatorProfile = @{ faultType = 'LOCK_TIMEOUT' }
  Ensure-Recipe $r2

  $r3 = Copy-Hashtable $base
  $r3.recipeId = 'RCP-LOST-LOCK'
  $r3.name = '随机失锁失败'
  $r3.simulatorProfile = @{ faultType = 'RANDOM_LOST_LOCK'; randomLostLockProbability = 1.0 }
  Ensure-Recipe $r3

  $r4 = Copy-Hashtable $base
  $r4.recipeId = 'RCP-MISSING-MAIN'
  $r4.name = '缺MAIN_INTERNAL导致大气时延失败'
  $r4.measurementPlan = @{ modes = @('LINK', 'RELAY_INTERNAL'); repeat = 8 }
  $r4.simulatorProfile = $null
  Ensure-Recipe $r4

  $a = (Check-Run 'A' 'RCP-DEFAULT' 'DONE' $true $null $null | Select-Object -Last 1)
  $b = (Check-Run 'B' 'RCP-LOCK-TIMEOUT' 'FAILED' $false 'LOCK_TIMEOUT' 'WAIT_LOCKED' | Select-Object -Last 1)
  $c = (Check-Run 'C' 'RCP-LOST-LOCK' 'FAILED' $false 'LOCK_LOST' 'MEASURE' | Select-Object -Last 1)
  $d = (Check-Run 'D' 'RCP-MISSING-MAIN' 'FAILED' $false 'ATMOSPHERIC_FAILED' 'SUMMARY' | Select-Object -Last 1)

  Write-Output ("ALL PASS: A={0} B={1} C={2} D={3}" -f $a, $b, $c, $d)
} finally {
  if ($started -and $proc) {
    try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
  }
  # Ensure no orphaned server is left behind (spring-boot:run may fork).
  Stop-ServerIfListening8080
}
