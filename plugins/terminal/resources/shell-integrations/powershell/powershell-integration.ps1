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

$Script = Get-Item "env:JEDITERM_SOURCE" -ErrorAction SilentlyContinue
if ($Script -ne $null) {
  Invoke-Expression $Script.Value
  Remove-Item "env:JEDITERM_SOURCE"
}

$Hooks = "$PSScriptRoot/command-block-support.ps1"
if (Test-Path $Hooks) {
  & $Hooks
}
