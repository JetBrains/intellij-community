# Tool Wrapper - PowerShell download/verify/execute logic for external tools
# See tool-wrapper.design.md for usage and environment variable reference

param(
    [switch]$VerifyAllPlatforms
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-StrictMode -Version 3.0

# Helper function for SHA256 hash (works with PowerShell 2.0+)
function Get-FileSHA256 {
    param([string]$Path)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $hash = $sha256.ComputeHash($stream)
            return [BitConverter]::ToString($hash).Replace('-', '').ToLower()
        } finally {
            $stream.Close()
        }
    } finally {
        $sha256.Dispose()
    }
}

# Get binary path for platform
function Get-BinaryForPlatform {
    param([string]$Platform)
    if ($Platform -like 'WINDOWS_*') {
        return $env:TOOL_BINARY_WINDOWS
    } else {
        return $env:TOOL_BINARY_UNIX
    }
}

# Get environment variables
$toolName = $env:TOOL_NAME
$toolVersion = $env:TOOL_VERSION
$toolBinary = $env:TOOL_BINARY_WINDOWS
$targetDir = $env:TARGET_DIR
$flagFile = $env:FLAG_FILE

if ($VerifyAllPlatforms) {
    # Validate required environment variables
    if ([string]::IsNullOrEmpty($env:TOOL_NAME)) {
        Write-Host 'ERROR: TOOL_NAME not set' -ForegroundColor Red
        exit 1
    }
    if ([string]::IsNullOrEmpty($env:TOOL_VERSION)) {
        Write-Host 'ERROR: TOOL_VERSION not set' -ForegroundColor Red
        exit 1
    }
    if ([string]::IsNullOrEmpty($env:TOOL_BINARY_UNIX)) {
        Write-Host 'ERROR: TOOL_BINARY_UNIX not set' -ForegroundColor Red
        exit 1
    }
    if ([string]::IsNullOrEmpty($env:TOOL_BINARY_WINDOWS)) {
        Write-Host 'ERROR: TOOL_BINARY_WINDOWS not set' -ForegroundColor Red
        exit 1
    }

    # Verify all platforms mode
    $platforms = @(
        @{Name='LINUX_X64'; Checksum=$env:TOOL_CHECKSUM_LINUX_X64; Url=$env:TOOL_URL_LINUX_X64},
        @{Name='LINUX_ARM64'; Checksum=$env:TOOL_CHECKSUM_LINUX_ARM64; Url=$env:TOOL_URL_LINUX_ARM64},
        @{Name='WINDOWS_X64'; Checksum=$env:TOOL_CHECKSUM_WINDOWS_X64; Url=$env:TOOL_URL_WINDOWS_X64},
        @{Name='WINDOWS_ARM64'; Checksum=$env:TOOL_CHECKSUM_WINDOWS_ARM64; Url=$env:TOOL_URL_WINDOWS_ARM64},
        @{Name='MACOS_ARM64'; Checksum=$env:TOOL_CHECKSUM_MACOS_ARM64; Url=$env:TOOL_URL_MACOS_ARM64}
    )

    $tempDir = Join-Path $env:TEMP ('tool-verify-' + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
    $allPassed = $true

    Write-Host "=== Verifying all platforms for $toolName $toolVersion ===" -ForegroundColor Cyan
    Write-Host ''

    try {
        foreach ($p in $platforms) {
            Write-Host "Platform: $($p.Name)" -ForegroundColor Yellow
            Write-Host "  URL: $($p.Url)"
            Write-Host "  Expected: $($p.Checksum)"

            if ([string]::IsNullOrEmpty($p.Checksum) -or [string]::IsNullOrEmpty($p.Url)) {
                Write-Host '  Status:   FAIL (not configured - missing checksum or URL)' -ForegroundColor Red
                Write-Host ''
                $allPassed = $false
                continue
            }

            $archivePath = Join-Path $tempDir "$($p.Name).archive"
            $extractPath = Join-Path $tempDir "$($p.Name).extract"
            try {
                $webClient = New-Object System.Net.WebClient
                $webClient.DownloadFile($p.Url, $archivePath)

                $actualChecksum = Get-FileSHA256 -Path $archivePath
                $fileSize = (Get-Item $archivePath).Length

                Write-Host "  Actual:   $actualChecksum"
                Write-Host "  Size:     $fileSize bytes"

                if ($actualChecksum -eq $p.Checksum) {
                    New-Item -ItemType Directory -Path $extractPath -Force | Out-Null
                    if ($p.Url -match '\.zip$') {
                        Add-Type -AssemblyName System.IO.Compression.FileSystem
                        [System.IO.Compression.ZipFile]::ExtractToDirectory($archivePath, $extractPath)
                    } else {
                        # Use Windows tar.exe (available on Windows 10+) for tar.gz files
                        $windowsTar = "$env:SystemRoot\system32\tar.exe"
                        & $windowsTar -xzf $archivePath -C $extractPath 2>&1 | Out-Null
                    }
                    $topLevel = @(Get-ChildItem -Path $extractPath)
                    $isNested = ($topLevel.Count -eq 1 -and $topLevel[0].PSIsContainer)
                    if ($isNested) {
                        Write-Host "  Structure: nested (top-level: $($topLevel[0].Name))"
                    } else {
                        Write-Host '  Structure: flat (no top-level directory)'
                    }

                    # Check binary exists
                    $platformBinary = Get-BinaryForPlatform -Platform $p.Name
                    if ($isNested) {
                        $binaryPath = Join-Path $topLevel[0].FullName $platformBinary
                    } else {
                        $binaryPath = Join-Path $extractPath $platformBinary
                    }

                    if (Test-Path $binaryPath) {
                        Write-Host "  Binary:   $platformBinary (found)"
                        Write-Host '  Status:   PASS' -ForegroundColor Green
                    } else {
                        Write-Host "  Binary:   $platformBinary (NOT FOUND)" -ForegroundColor Red
                        Write-Host '  Status:   FAIL (binary missing)' -ForegroundColor Red
                        $allPassed = $false
                    }
                } else {
                    Write-Host '  Status:   FAIL (checksum mismatch)' -ForegroundColor Red
                    $allPassed = $false
                }
            } catch {
                Write-Host "  Status:   FAIL (error: $_)" -ForegroundColor Red
                $allPassed = $false
            }

            if (Test-Path $archivePath) { Remove-Item $archivePath -Force }
            if (Test-Path $extractPath) { Remove-Item $extractPath -Recurse -Force }
            Write-Host ''
        }
    } finally {
        if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force -ErrorAction SilentlyContinue }
    }

    if ($allPassed) {
        Write-Host '=== All platforms verified successfully ===' -ForegroundColor Green
        exit 0
    } else {
        Write-Host '=== Some platforms failed verification ===' -ForegroundColor Red
        exit 1
    }
}

# Normal download mode
$url = $env:DOWNLOAD_URL
$expectedChecksum = $env:EXPECTED_CHECKSUM

$createdNew = $false
$mutexName = 'Global\tool-wrapper-' + $toolName
$mutex = New-Object System.Threading.Mutex($true, $mutexName, [ref]$createdNew)

if (-not $createdNew) {
    Write-Host "Waiting for another process to finish downloading $toolName..." -ForegroundColor Yellow
    [void]$mutex.WaitOne()
}

try {
    if ((Test-Path $flagFile) -and ((Get-Content $flagFile -ErrorAction Ignore) -eq $expectedChecksum)) {
        Write-Host 'Already downloaded (verified after lock)' -ForegroundColor Green
        exit 0
    }

    $tempDir = Join-Path $env:TEMP ('tool-wrapper-' + [guid]::NewGuid().ToString())
    New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

    try {
        $archivePath = Join-Path $tempDir 'archive.zip'

        Write-Host "Downloading $url" -ForegroundColor Cyan
        $webClient = New-Object System.Net.WebClient
        $webClient.DownloadFile($url, $archivePath)

        $actualChecksum = Get-FileSHA256 -Path $archivePath
        if ($actualChecksum -ne $expectedChecksum) {
            throw "Checksum mismatch`nExpected: $expectedChecksum`nActual:   $actualChecksum"
        }
        Write-Host "Checksum verified: $actualChecksum" -ForegroundColor Green

        $extractTemp = Join-Path $tempDir 'extract'
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [System.IO.Compression.ZipFile]::ExtractToDirectory($archivePath, $extractTemp)

        $topLevel = @(Get-ChildItem -Path $extractTemp)
        $isNested = ($topLevel.Count -eq 1 -and $topLevel[0].PSIsContainer)

        if (Test-Path $targetDir) {
            Remove-Item -Path $targetDir -Recurse -Force
        }
        $parentDir = Split-Path $targetDir -Parent
        New-Item -ItemType Directory -Path $parentDir -Force | Out-Null
        New-Item -ItemType Directory -Path $targetDir -Force | Out-Null

        if ($isNested) {
            Get-ChildItem -Path $topLevel[0].FullName | Move-Item -Destination $targetDir -Force
        } else {
            Get-ChildItem -Path $extractTemp | Move-Item -Destination $targetDir -Force
        }

        $binaryPath = Join-Path $targetDir $toolBinary
        if (-not (Test-Path $binaryPath)) {
            throw "Binary not found after extraction: $toolBinary"
        }

        Set-Content -Path $flagFile -Value $expectedChecksum
        Write-Host "Cached: $targetDir" -ForegroundColor Green
    }
    finally {
        if (Test-Path $tempDir) {
            Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}
finally {
    $mutex.ReleaseMutex()
}
