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

  Write-Host "IntelliJ Terminal requires RSReadLine module to have version 2.0.3+, while current version is $Version"
  Write-Host "Do you agree to install the latest version of PSReadLine module?"
  Write-Host "The following command will be executed: 'Install-Module PSReadLine -MinimumVersion 2.0.3 -Scope CurrentUser -Force'"
  $Answer = Read-Host "[Y] Yes [N] No"
  if ($Answer -ieq 'n') {
    Write-Host "Installation of latest PSReadLine version was rejected"
    Write-Host "$([char]0x1B)]1341;psreadline_update_rejected`a" -NoNewline
    return
  }
  if ($Answer -ine 'y') {
    Write-Host "Installation of latest PSReadLine version was skipped"
    return
  }

  Install-Module PSReadLine -MinimumVersion 2.0.3 -Scope CurrentUser -Force
  if ($? -eq $true) {
    Write-Host "New version of PSReadLine was successfully installed. Please open new Terminal tab."
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
