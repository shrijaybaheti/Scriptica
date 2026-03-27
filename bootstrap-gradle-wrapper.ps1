param(
  [string]$GradleVersion = "9.2.0-rc-1"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

$bootstrapDir = Join-Path $root ".gradle-bootstrap"
$zipPath = Join-Path $bootstrapDir "gradle-$GradleVersion-bin.zip"
$extractDir = Join-Path $bootstrapDir "gradle-$GradleVersion"

New-Item -ItemType Directory -Force -Path $bootstrapDir | Out-Null

function Test-ZipFile {
  param([Parameter(Mandatory=$true)][string]$Path)
  if (!(Test-Path $Path)) { return $false }
  try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction SilentlyContinue | Out-Null
    $z = [System.IO.Compression.ZipFile]::OpenRead($Path)
    $z.Dispose()
    return $true
  } catch {
    return $false
  }
}

function Download-File {
  param(
    [Parameter(Mandatory=$true)][string]$Url,
    [Parameter(Mandatory=$true)][string]$OutFile
  )

  # Prefer BITS when available (more reliable for large downloads on Windows)
  $bits = Get-Command Start-BitsTransfer -ErrorAction SilentlyContinue
  if ($null -ne $bits) {
    Write-Host "Downloading via BITS..."
    Start-BitsTransfer -Source $Url -Destination $OutFile -ErrorAction Stop
    return
  }

  Write-Host "Downloading via Invoke-WebRequest..."
  Invoke-WebRequest -Uri $Url -OutFile $OutFile -MaximumRedirection 5 -TimeoutSec 600
}

if (!(Test-Path $zipPath)) {
  $url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
  Write-Host "Downloading $url"
  Download-File -Url $url -OutFile $zipPath
}

if (!(Test-ZipFile -Path $zipPath)) {
  Write-Host "Downloaded zip looks corrupted. Re-downloading..."
  Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
  $url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
  Download-File -Url $url -OutFile $zipPath
  if (!(Test-ZipFile -Path $zipPath)) {
    throw "Gradle zip is still invalid: $zipPath"
  }
}

if (!(Test-Path $extractDir)) {
  Write-Host "Extracting $zipPath"
  try {
    Expand-Archive -Path $zipPath -DestinationPath $bootstrapDir -Force
  } catch {
    Write-Host "Extraction failed. Deleting zip and retrying download..."
    Remove-Item -LiteralPath $zipPath -Force -ErrorAction SilentlyContinue
    $url = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"
    Invoke-WebRequest -Uri $url -OutFile $zipPath -MaximumRedirection 5
    Expand-Archive -Path $zipPath -DestinationPath $bootstrapDir -Force
  }
}

$gradleBat = Join-Path $extractDir "bin\gradle.bat"
if (!(Test-Path $gradleBat)) {
  throw "Gradle bootstrap failed: $gradleBat not found"
}

Write-Host "Generating Gradle wrapper using Gradle $GradleVersion"
& $gradleBat wrapper --gradle-version $GradleVersion

Write-Host "Done. Next: .\\gradlew.bat runClient"
