# For every _INTELLIJ_FORCE_SET_FOO=BAR run: export FOO=BAR.
Get-ChildItem env:_INTELLIJ_FORCE_SET_* | ForEach-Object {
  $FullName = $_.Name
  $Name = $FullName -replace '_INTELLIJ_FORCE_SET_',''
  Set-Item -Path "env:\$Name" -Value $_.Value
  Remove-Item "env:$FullName"
}

# For every _INTELLIJ_FORCE_PREPEND_FOO=BAR run: export FOO=BAR$FOO.
Get-ChildItem env:_INTELLIJ_FORCE_PREPEND_* | ForEach-Object {
  $FullName = $_.Name
  $Name = $FullName -replace '_INTELLIJ_FORCE_PREPEND_',''
  $CurValue = Get-Item "env:$Name"
  Set-Item -Path "env:\$Name" -Value ($_.Value + $CurValue.Value)
  Remove-Item "env:$FullName"
}
# `JEDITERM_SOURCE` is executed in its own scope now. That means, it can only run code, and export env vars. It can't export PS variables.
# It might be better to source it. See MSDN for the difference between "Call operator &"  and "Script scope and dot sourcing"
if ($Env:JEDITERM_SOURCE -ne $null) {
  if (Test-Path "$Env:JEDITERM_SOURCE" -ErrorAction SilentlyContinue) {
      & "$Env:JEDITERM_SOURCE"
    } else { # If file doesn't exist it might be a script
      Invoke-Expression "$Env:JEDITERM_SOURCE"
    }
  Remove-Item "env:JEDITERM_SOURCE"
}

function __JetBrainsIntellijAskPSReadLineUpdating() {
  $ReadLineModule = Get-Module -Name PSReadLine
  if ($ReadLineModule -eq $null) {
    # PSReadLine module can be not loaded.
    # For example, if PowerShell is running in a Constrained Language Mode.
    # Or if Screen Reader support is active.
    return
  }
  $Version = $ReadLineModule.Version
  $RequiredVersion = [System.Version]"2.0.3"
  if ($Version -ge $RequiredVersion) {
    # No update needed.
    return
  }

  # The localized text is passed as env variables
  $Esc = [char]0x1b
  $VersionColored = "$Esc[1m$Version$Esc[0m"
  $RequiredVersionColored = "$Esc[1m$RequiredVersion$Esc[0m"
  $Line1 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_1 -f $Env:__JETBRAINS_INTELLIJ_IDE_NAME, $RequiredVersionColored, $VersionColored
  $Line2 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_2
  $CommandText = "'Install-Module PSReadLine -MinimumVersion $RequiredVersion -Scope CurrentUser -Force'"
  $CommandTextColored = "$Esc[32m$CommandText$Esc[0m"
  $Line3 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_3 -f $CommandTextColored
  $Line4 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_4

  Write-Host $Line1
  Write-Host $Line2
  Write-Host $Line3
  $Answer = Read-Host $Line4
  if ($Answer -ieq 'n') {
    Write-Host $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_REJECTED
    Write-Host "$([char]0x1B)]1341;psreadline_update_rejected`a" -NoNewline
    return
  }
  if ($Answer -ine 'y') {
    Write-Host $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_SKIPPED
    return
  }

  Install-Module PSReadLine -MinimumVersion $RequiredVersion -Scope CurrentUser -Force
  if ($? -eq $true) {
    Write-Host $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_COMPLETED
  }
}

if ($Env:__JETBRAINS_INTELLIJ_ASK_PSREADLINE_UPDATE -eq $true) {
  __JetBrainsIntellijAskPSReadLineUpdating
}

$Hooks = "$PSScriptRoot/command-block-support.ps1"
if (Test-Path $Hooks) {
  & $Hooks
}
$HooksReworked = "$PSScriptRoot/command-block-support-reworked.ps1"
if (Test-Path $HooksReworked) {
  & $HooksReworked
}
