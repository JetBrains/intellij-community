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

$Hooks = "$PSScriptRoot/command-block-support.ps1"
if (Test-Path $Hooks) {
  & $Hooks
}
$HooksReworked = "$PSScriptRoot/command-block-support-reworked.ps1"
if (Test-Path $HooksReworked) {
  & $HooksReworked
}
