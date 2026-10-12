$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\budget-book"
$app  = Join-Path $root "app"
$sdk  = Join-Path $root ".android-sdk"
$tc   = Join-Path $root ".toolchain"
$out  = Join-Path $root "build"
$gen  = Join-Path $out "gen"
$cls  = Join-Path $out "classes"
$dex  = Join-Path $out "dex"

$androidJar = Join-Path $sdk "platforms\android-34\android.jar"
$bt         = Join-Path $sdk "build-tools\34.0.0"
$aapt2      = Join-Path $bt "aapt2.exe"
$d8         = Join-Path $bt "d8.bat"
$zipalign   = Join-Path $bt "zipalign.exe"
$apksigner  = Join-Path $bt "apksigner.bat"

$javac = Get-ChildItem -Path (Join-Path $tc "jdk17") -Recurse -Filter javac.exe |
         Where-Object { $_.FullName -like "*\bin\javac.exe" } | Select-Object -First 1
$keytool = Join-Path (Split-Path (Split-Path $javac.FullName -Parent) -Parent) "bin\keytool.exe"
$env:JAVA_HOME = Split-Path (Split-Path $javac.FullName -Parent) -Parent

Write-Output "JAVA_HOME : $env:JAVA_HOME"
foreach ($t in @($androidJar, $aapt2, $d8, $zipalign, $apksigner, $javac.FullName, $keytool)) {
  if (-not (Test-Path $t)) { throw "missing tool: $t" }
}

# ---------- clean ----------
foreach ($d in @($gen, $cls, $dex)) {
  if (Test-Path $d) { Remove-Item -Recurse -Force $d }
  New-Item -ItemType Directory -Force -Path $d | Out-Null
}
New-Item -ItemType Directory -Force -Path $out | Out-Null

$compiledRes = Join-Path $out "resources.zip"
$baseApk     = Join-Path $out "base.apk"
$withDex     = Join-Path $out "with-dex.apk"
$aligned     = Join-Path $out "aligned.apk"
$finalApk    = Join-Path $root "JianZhang-1.0.apk"
$keystore    = Join-Path $root "tools\release.jks"

foreach ($f in @($compiledRes, $baseApk, $withDex, $aligned, $finalApk)) {
  if (Test-Path $f) { Remove-Item -Force $f }
}

# ---------- 1. compile resources ----------
Write-Output "`n[1/6] aapt2 compile"
& $aapt2 compile --dir (Join-Path $app "res") -o $compiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

# ---------- 2. link resources -> base apk + R.java ----------
# NB: the compiled zip must be passed positionally. Passing it with -R would
# give it "overlay" semantics and every resource would fail to link.
Write-Output "[2/6] aapt2 link"
& $aapt2 link `
  -o $baseApk `
  -I $androidJar `
  --manifest (Join-Path $app "AndroidManifest.xml") `
  --java $gen `
  --min-sdk-version 21 `
  --target-sdk-version 34 `
  --no-version-vectors `
  $compiledRes
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

# ---------- 3. javac ----------
Write-Output "[3/6] javac"
$sources = @()
$sources += (Get-ChildItem -Path (Join-Path $app "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName })
$sources += (Get-ChildItem -Path $gen -Recurse -Filter *.java | ForEach-Object { $_.FullName })
Write-Output ("       sources: " + $sources.Count)

$argFile = $null

& $javac.FullName `
  -encoding UTF-8 `
  -source 8 `
  -target 8 `
  -nowarn `
  -bootclasspath $androidJar `
  -classpath $androidJar `
  -d $cls `
  @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# ---------- 4. d8 ----------
Write-Output "[4/6] d8"
$classFiles = Get-ChildItem -Path $cls -Recurse -Filter *.class | ForEach-Object { $_.FullName }
Write-Output ("       classes: " + $classFiles.Count)
& $d8 --release --lib $androidJar --min-api 21 --output $dex @classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
if (-not (Test-Path (Join-Path $dex "classes.dex"))) { throw "classes.dex was not produced" }

# ---------- 5. package + zipalign ----------
Write-Output "[5/6] package + zipalign"
Copy-Item $baseApk $withDex -Force
$py = "C:\Users\Administrator\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\python\python.exe"
& $py (Join-Path $root "tools\add-dex.py") $withDex (Join-Path $dex "classes.dex")
if ($LASTEXITCODE -ne 0) { throw "add-dex failed" }

& $zipalign -p -f 4 $withDex $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

# ---------- 6. sign ----------
Write-Output "[6/6] sign"
if (-not (Test-Path $keystore)) {
  & $keytool -genkeypair -v `
    -keystore $keystore `
    -alias jianzhang `
    -storepass jianzhang2024 `
    -keypass jianzhang2024 `
    -keyalg RSA -keysize 2048 -validity 10950 `
    -dname "CN=JianZhang, OU=Personal, O=JianZhang, L=Local, C=CN"
  if ($LASTEXITCODE -ne 0) { throw "keytool failed" }
}

& $apksigner sign `
  --ks $keystore `
  --ks-key-alias jianzhang `
  --ks-pass pass:jianzhang2024 `
  --key-pass pass:jianzhang2024 `
  --v1-signing-enabled true `
  --v2-signing-enabled true `
  --out $finalApk `
  $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner sign failed" }

Write-Output "`n== verify =="
& $apksigner verify --verbose --print-certs $finalApk
Write-Output ("`nAPK : " + $finalApk)
Write-Output ("SIZE: " + [math]::Round((Get-Item $finalApk).Length / 1KB, 1) + " KB")
Write-Output "BUILD-OK"
