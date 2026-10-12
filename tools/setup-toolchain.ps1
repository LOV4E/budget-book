$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\budget-book"
$tc   = Join-Path $root ".toolchain"
$sdk  = Join-Path $root ".android-sdk"

New-Item -ItemType Directory -Force -Path $tc  | Out-Null
New-Item -ItemType Directory -Force -Path $sdk | Out-Null

$jdkZip = Join-Path $tc "jdk17.zip"
$ctZip  = Join-Path $tc "cmdline-tools.zip"
$jdkDir = Join-Path $tc "jdk17"

# ---------- JDK ----------
if (-not (Test-Path $jdkZip) -or (Get-Item $jdkZip).Length -lt 100MB) {
  Write-Output "== downloading JDK 17 (Microsoft OpenJDK) =="
  curl.exe -sSL --retry 3 --retry-delay 2 -o $jdkZip "https://aka.ms/download-jdk/microsoft-jdk-17.0.13-windows-x64.zip"
}
Write-Output ("JDK zip bytes : " + (Get-Item $jdkZip).Length)

if (-not (Test-Path (Join-Path $jdkDir "bin\javac.exe"))) {
  Write-Output "== extracting JDK =="
  if (Test-Path $jdkDir) { Remove-Item -Recurse -Force $jdkDir }
  Expand-Archive -Path $jdkZip -DestinationPath $jdkDir -Force
}
$javac = Get-ChildItem -Path $jdkDir -Recurse -Filter javac.exe -ErrorAction SilentlyContinue |
         Where-Object { $_.FullName -like "*\bin\javac.exe" } | Select-Object -First 1
if (-not $javac) { throw "javac.exe not found after extraction" }
$env:JAVA_HOME = Split-Path (Split-Path $javac.FullName -Parent) -Parent
Write-Output ("JAVA_HOME     : " + $env:JAVA_HOME)
& $javac.FullName -version 2>&1 | ForEach-Object { Write-Output ("javac         : " + $_) }

# ---------- Android command line tools ----------
if (-not (Test-Path $ctZip) -or (Get-Item $ctZip).Length -lt 50MB) {
  Write-Output "== downloading Android cmdline-tools =="
  curl.exe -sSL --retry 3 --retry-delay 2 -o $ctZip "https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip"
}
Write-Output ("CT zip bytes  : " + (Get-Item $ctZip).Length)

$latestBin = Join-Path $sdk "cmdline-tools\latest\bin\sdkmanager.bat"
if (-not (Test-Path $latestBin)) {
  Write-Output "== extracting cmdline-tools =="
  $raw = Join-Path $tc "cmdline-tools-raw"
  if (Test-Path $raw) { Remove-Item -Recurse -Force $raw }
  Expand-Archive -Path $ctZip -DestinationPath $raw -Force
  $src = Get-ChildItem -Path $raw -Recurse -Filter sdkmanager.bat -ErrorAction SilentlyContinue |
         Select-Object -First 1
  if (-not $src) { throw "sdkmanager.bat not found" }
  $srcRoot = Split-Path (Split-Path $src.FullName -Parent) -Parent   # ...\cmdline-tools
  $destParent = Join-Path $sdk "cmdline-tools"
  New-Item -ItemType Directory -Force -Path $destParent | Out-Null
  if (Test-Path (Join-Path $destParent "latest")) { Remove-Item -Recurse -Force (Join-Path $destParent "latest") }
  Move-Item -Path $srcRoot -Destination (Join-Path $destParent "latest")
}
if (-not (Test-Path $latestBin)) { throw "sdkmanager still missing at $latestBin" }
Write-Output ("sdkmanager    : " + $latestBin)

# ---------- SDK packages ----------
Write-Output "== accepting licenses =="
$y = ("y`r`n" * 40)
$y | & $latestBin --sdk_root=$sdk --licenses 2>&1 | Select-Object -Last 8 | ForEach-Object { Write-Output ("  " + $_) }

Write-Output "== installing platform + build-tools =="
& $latestBin --sdk_root=$sdk "platform-tools" "platforms;android-34" "build-tools;34.0.0" 2>&1 |
  Select-Object -Last 15 | ForEach-Object { Write-Output ("  " + $_) }

Write-Output "== verify =="
foreach ($p in @("platforms\android-34\android.jar",
                 "build-tools\34.0.0\aapt2.exe",
                 "build-tools\34.0.0\d8.bat",
                 "build-tools\34.0.0\zipalign.exe",
                 "build-tools\34.0.0\apksigner.bat")) {
  $full = Join-Path $sdk $p
  if (Test-Path $full) { Write-Output ("  OK   $p") } else { Write-Output ("  MISS $p") }
}
Write-Output "TOOLCHAIN-READY"
