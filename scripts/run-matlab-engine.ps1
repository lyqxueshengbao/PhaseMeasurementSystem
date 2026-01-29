param(
  [string]$MatlabRoot = 'D:\matlab R2022b',
  [string]$MatlabScriptDir = 'matlab',
  [string]$MatlabModel = 'placeholder',
  [int]$MatlabMonteCarloT = 2000,
  [switch]$Stop8080
)

$ErrorActionPreference = 'Stop'

function Stop-ServerIfListening8080() {
  try {
    $c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $c) { return }
    $owningPid = [int]$c.OwningProcess
    Stop-Process -Id $owningPid -Force -ErrorAction SilentlyContinue
  } catch {}
}

function Get-ShortPath([string]$path) {
  $full = (Resolve-Path $path).Path
  if (-not ('Win32.NativeMethods' -as [type])) {
    Add-Type -Namespace Win32 -Name NativeMethods -MemberDefinition @"
[System.Runtime.InteropServices.DllImport("kernel32.dll", CharSet = System.Runtime.InteropServices.CharSet.Auto, SetLastError = true)]
public static extern int GetShortPathName(string lpszLongPath, System.Text.StringBuilder lpszShortPath, int cchBuffer);
"@
  }

  $sb = New-Object System.Text.StringBuilder 4096
  $rc = [Win32.NativeMethods]::GetShortPathName($full, $sb, $sb.Capacity)
  if ($rc -le 0) { return $full }
  $short = $sb.ToString()
  if ([string]::IsNullOrWhiteSpace($short)) { return $full }
  return $short
}

$matlabRootFull = (Resolve-Path $MatlabRoot).Path
$engineJarFull = Join-Path $matlabRootFull 'extern\engines\java\jar\engine.jar'
$nativeDirsFull = @(
  (Join-Path $matlabRootFull 'bin\win64'),
  (Join-Path $matlabRootFull 'extern\bin\win64')
)

if (-not (Test-Path $engineJarFull)) { throw "engine.jar not found: $engineJarFull" }
foreach ($d in $nativeDirsFull) {
  if (-not (Test-Path $d)) { throw "MATLAB native dir not found: $d" }
}

$engineJar = Get-ShortPath $engineJarFull
$nativeDirs = $nativeDirsFull | ForEach-Object { Get-ShortPath $_ }
$nativePath = ($nativeDirs -join ';')
$env:PATH = "$nativePath;$env:PATH"

$appArgs = "--pj125.measurement.engine=matlab-engine --pj125.measurement.matlabScriptDir=$MatlabScriptDir --pj125.measurement.matlabModel=$MatlabModel --pj125.measurement.matlabMonteCarloT=$MatlabMonteCarloT"

if ($Stop8080) {
  Stop-ServerIfListening8080
} else {
  try {
    $c = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($c) {
      Write-Host "Port 8080 is already in use (PID=$($c.OwningProcess)). Stop the running Spring Boot first, or re-run with -Stop8080." -ForegroundColor Yellow
    }
  } catch {}
}

mvn -q spring-boot:run `
  "-Dspring-boot.run.additional-classpath-elements=$engineJar" `
  "-Dspring-boot.run.systemPropertyVariables.java.library.path=$nativePath" `
  "-Dspring-boot.run.arguments=$appArgs"
