# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

param(
  [string]$Target,
  [switch]$All
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..\..\..\..")
$CargoToml = Join-Path $ScriptDir "Cargo.toml"
$SupportedTargets = @(
  "x86_64-pc-windows-msvc",
  "aarch64-pc-windows-msvc"
)

function Get-DefaultTarget {
  switch ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture) {
    "X64" { return "x86_64-pc-windows-msvc" }
    "Arm64" { return "aarch64-pc-windows-msvc" }
    default { throw "Unsupported host architecture: $([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture). Pass -Target explicitly." }
  }
}

function Use-RustupToolchain {
  $RustupBin = Join-Path ([Environment]::GetFolderPath("UserProfile")) ".cargo\bin"
  if (Test-Path -LiteralPath (Join-Path $RustupBin "cargo.exe")) {
    $env:PATH = "$RustupBin;$env:PATH"
  }
}

function Find-VcVarsAll {
  $VsWhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
  if (Test-Path -LiteralPath $VsWhere) {
    $InstallationPath = & $VsWhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
    if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($InstallationPath)) {
      $VcVarsAll = Join-Path $InstallationPath "VC\Auxiliary\Build\vcvarsall.bat"
      if (Test-Path -LiteralPath $VcVarsAll) {
        return $VcVarsAll
      }
    }
  }

  $KnownPaths = @(
    "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat",
    "$env:ProgramFiles\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat",
    "${env:ProgramFiles(x86)}\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvarsall.bat",
    "$env:ProgramFiles\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvarsall.bat"
  )
  foreach ($Path in $KnownPaths) {
    if (Test-Path -LiteralPath $Path) {
      return $Path
    }
  }

  return $null
}

function Invoke-CargoBuild {
  param(
    [string]$BuildTarget
  )

  if ($BuildTarget -eq "aarch64-pc-windows-msvc") {
    $VcVarsAll = Find-VcVarsAll
    if ($null -ne $VcVarsAll) {
      $Command = "set `"PATH=%USERPROFILE%\.cargo\bin;%PATH%`" && call `"$VcVarsAll`" x64_arm64 && cargo build --manifest-path `"$CargoToml`" --release --target $BuildTarget"
      cmd /d /c $Command
      if ($LASTEXITCODE -ne 0) {
        throw "Cargo build failed for target $BuildTarget with exit code $LASTEXITCODE"
      }
      return
    }

    Write-Host "Visual Studio vcvarsall.bat was not found; trying current environment for $BuildTarget"
  }

  cargo build --manifest-path $CargoToml --release --target $BuildTarget
  if ($LASTEXITCODE -ne 0) {
    throw "Cargo build failed for target $BuildTarget with exit code $LASTEXITCODE"
  }
}

$TargetToPluginArch = @{
  "x86_64-pc-windows-msvc" = "x86_64"
  "aarch64-pc-windows-msvc" = "aarch64"
}

if ($All -and -not [string]::IsNullOrWhiteSpace($Target)) {
  throw "Pass either -All or -Target, not both."
}

$Targets = if ($All) {
  $SupportedTargets
}
elseif ([string]::IsNullOrWhiteSpace($Target)) {
  @(Get-DefaultTarget)
}
else {
  @($Target)
}

Use-RustupToolchain

Push-Location $RepoRoot
try {
  foreach ($BuildTarget in $Targets) {
    $PluginArch = $TargetToPluginArch[$BuildTarget]
    if ($null -eq $PluginArch) {
      throw "Unsupported target: $BuildTarget. Supported targets: $($TargetToPluginArch.Keys -join ', ')."
    }

    $ArtifactPath = Join-Path $ScriptDir "target\$BuildTarget\release\win_webview2_bridge.dll"
    $CommittedPath = Join-Path $RepoRoot "community\plugins\ui.webview\lib\webview-native\win\$PluginArch\win_webview2_bridge.dll"

    Invoke-CargoBuild -BuildTarget $BuildTarget

    if (-not (Test-Path -LiteralPath $ArtifactPath)) {
      throw "Cargo completed but the DLL was not found: $ArtifactPath"
    }

    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $CommittedPath) | Out-Null
    Copy-Item -LiteralPath $ArtifactPath -Destination $CommittedPath -Force
    Write-Host "Copied $ArtifactPath -> $CommittedPath"
  }
}
finally {
  Pop-Location
}
