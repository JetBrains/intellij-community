# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

param(
  [string]$Target
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..\..\..\..")
$CargoToml = Join-Path $ScriptDir "Cargo.toml"

function Get-DefaultTarget {
  switch ([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture) {
    "X64" { return "x86_64-pc-windows-msvc" }
    "Arm64" { return "aarch64-pc-windows-msvc" }
    default { throw "Unsupported host architecture: $([System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture). Pass -Target explicitly." }
  }
}

if ([string]::IsNullOrWhiteSpace($Target)) {
  $Target = Get-DefaultTarget
}

$TargetToPluginArch = @{
  "x86_64-pc-windows-msvc" = "x86_64"
  "aarch64-pc-windows-msvc" = "aarch64"
}

$PluginArch = $TargetToPluginArch[$Target]
if ($null -eq $PluginArch) {
  throw "Unsupported target: $Target. Supported targets: $($TargetToPluginArch.Keys -join ', ')."
}

$ArtifactPath = Join-Path $ScriptDir "target\$Target\release\win_webview2_bridge.dll"
$CommittedPath = Join-Path $RepoRoot "community\plugins\ui.webview\lib\webview-native\win\$PluginArch\win_webview2_bridge.dll"

Push-Location $RepoRoot
try {
  cargo build --manifest-path $CargoToml --release --target $Target
}
finally {
  Pop-Location
}

if (-not (Test-Path -LiteralPath $ArtifactPath)) {
  throw "Cargo completed but the DLL was not found: $ArtifactPath"
}

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $CommittedPath) | Out-Null
Copy-Item -LiteralPath $ArtifactPath -Destination $CommittedPath -Force
Write-Host "Copied $ArtifactPath -> $CommittedPath"
