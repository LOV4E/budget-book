$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = "C:\Users\Administrator\Documents\deepseek-harness\default-workspace\budget-book"
$sdk  = Join-Path $root ".android-sdk"
$tc   = Join-Path $root ".toolchain"
New-Item -ItemType Directory -Force -Path $sdk, $tc | Out-Null

$repoXml = Join-Path $tc "repository2-3.xml"
if ((-not (Test-Path $repoXml)) -or ((Get-Item $repoXml).Length -lt 10000)) {
  Write-Output "== fetching repository2-3.xml =="
  curl.exe -sSL --retry 3 -o $repoXml "https://dl.google.com/android/repository/repository2-3.xml"
}
Write-Output ("repo xml bytes: " + (Get-Item $repoXml).Length)

[xml]$doc = Get-Content -Raw -Path $repoXml

function Get-ArchiveInfo([string]$pkgPath) {
  $n = $doc.SelectSingleNode("//*[local-name()='remotePackage' and @path='$pkgPath']")
  if (-not $n) { throw "remotePackage not found: $pkgPath" }
  foreach ($a in $n.SelectNodes(".//*[local-name()='archive']")) {
    $hostOs = $a.SelectSingleNode("*[local-name()='host-os']")
    if ($hostOs -and ($hostOs.InnerText -ne 'windows')) { continue }
    $u = $a.SelectSingleNode(".//*[local-name()='complete']/*[local-name()='url']")
    $s = $a.SelectSingleNode(".//*[local-name()='complete']/*[local-name()='size']")
    if ($u) {
      return [pscustomobject]@{
        Pkg  = $pkgPath
        Url  = $u.InnerText
        Size = if ($s) { [int64]$s.InnerText } else { 0 }
      }
    }
  }
  throw "no windows archive for $pkgPath"
}

function Install-Pkg([string]$pkgPath, [string]$marker, [string]$destDir) {
  $info = Get-ArchiveInfo $pkgPath
  $file = Join-Path $tc (($pkgPath -replace '[;:]', '_') + ".zip")

  # The repository XML stores archive urls relative to this base.
  $url = $info.Url
  if ($url -notmatch '^https?://') { $url = "https://dl.google.com/android/repository/" + $url }

  Write-Output ("== " + $pkgPath + " ==")
  Write-Output ("   url  : " + $url)
  Write-Output ("   size : " + [math]::Round($info.Size / 1MB, 1) + " MB")
  if ((-not (Test-Path $file)) -or ((Get-Item $file).Length -ne $info.Size)) {
    curl.exe -sSL --retry 3 --retry-delay 2 -o $file $url
    if ($LASTEXITCODE -ne 0) { throw "download failed for $url" }
  }
  Write-Output ("   got  : " + [math]::Round((Get-Item $file).Length / 1MB, 1) + " MB")

  $tmp = Join-Path $tc ("x_" + [guid]::NewGuid().ToString("N"))
  New-Item -ItemType Directory -Force -Path $tmp | Out-Null
  Expand-Archive -Path $file -DestinationPath $tmp -Force

  $hit = Get-ChildItem -Path $tmp -Recurse -Filter $marker -ErrorAction SilentlyContinue |
         Sort-Object { $_.FullName.Length } | Select-Object -First 1
  if (-not $hit) { throw "marker '$marker' not found inside $file" }
  $srcDir = $hit.Directory.FullName

  if (Test-Path $destDir) { Remove-Item -Recurse -Force $destDir }
  New-Item -ItemType Directory -Force -Path (Split-Path $destDir -Parent) | Out-Null
  Move-Item -Path $srcDir -Destination $destDir
  Remove-Item -Recurse -Force $tmp
  Write-Output ("   ->   : " + $destDir)
}

Install-Pkg "platforms;android-34" "android.jar"  (Join-Path $sdk "platforms\android-34")
Install-Pkg "build-tools;34.0.0"   "aapt2.exe"    (Join-Path $sdk "build-tools\34.0.0")

Write-Output "== verify =="
foreach ($p in @("platforms\android-34\android.jar",
                 "build-tools\34.0.0\aapt2.exe",
                 "build-tools\34.0.0\d8.bat",
                 "build-tools\34.0.0\lib\d8.jar",
                 "build-tools\34.0.0\zipalign.exe",
                 "build-tools\34.0.0\apksigner.bat")) {
  $full = Join-Path $sdk $p
  if (Test-Path $full) { Write-Output ("  OK   $p") } else { Write-Output ("  MISS $p") }
}
Write-Output "SDK-READY"
