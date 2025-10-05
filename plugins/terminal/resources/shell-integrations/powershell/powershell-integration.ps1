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
  $IdeName = "[$Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_IDE_NAME]"
  $IdeNameColored="$Esc[1m$IdeName$Esc[0m"
  $Line1 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_1 -f $IdeNameColored, $Version
  $Line2 = "https://learn.microsoft.com/windows/terminal/troubleshooting#black-lines-in-powershell-51-6x-70"
  $CommandText = "'Install-Module PSReadLine -MinimumVersion $RequiredVersion -Scope CurrentUser -Force'"
  $CommandTextColored = "$Esc[32m$CommandText$Esc[0m"
  $Line3 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_2 -f $CommandTextColored
  $Line4 = $Env:__JETBRAINS_INTELLIJ_PSREADLINE__UPDATE_TEXT_LINE_3

  Write-Host $Line1
  Write-Host $Line2
  Write-Host $Line3
  Write-Host $Line4
  Write-Host  # to an empty line between the message and the prompt
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
