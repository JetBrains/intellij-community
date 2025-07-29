if ([string]::IsNullOrEmpty($Env:INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED)) {
  return
}

# Do not source the shell integration if shell is launched in not "FullLanguage" mode
if ($ExecutionContext.SessionState.LanguageMode -ne "FullLanguage") {
	return
}

# We require PSReadLine module for our integration, so if it is not loaded for some reason, we can't continue.
if ((Get-Module -Name PSReadLine) -eq $null) {
  return
}

$Global:__JetBrainsIntellijState = @{
  IsInitialized = $false
	IsCommandRunning = $false
	OriginalPSConsoleHostReadLine = $function:PSConsoleHostReadLine
}

function Global:__JetBrainsIntellijOSC([string]$body) {
  return "$([char]0x1B)]1341;$body`a"
}

function Global:__JetBrainsIntellijEncode([string]$value) {
  $Bytes = [System.Text.Encoding]::UTF8.GetBytes($value)
  return [System.BitConverter]::ToString($Bytes).Replace("-", "")
}

$Global:__JetBrainsIntellijTerminalInitialized=$false

if (Test-Path Function:\Prompt) {
  Rename-Item Function:\Prompt Global:__JetBrainsIntellijOriginalPrompt
}
else {
  function Global:__JetBrainsIntellijOriginalPrompt() { return "" }
}

function Global:Prompt() {
  $Success = $Global:?
  $ExitCode = $Global:LastExitCode

  $Result = ""
  if ($Global:__JetBrainsIntellijState.IsInitialized -eq $false) {
    $Global:__JetBrainsIntellijState.IsInitialized = $true
    $Result += Global:__JetBrainsIntellijOSC "initialized"
    # Return the empty aliases list for now
    $Result += Global:__JetBrainsIntellijOSC "aliases_received"
  }
  elseif ($Global:__JetBrainsIntellijState.IsCommandRunning -eq $true){
    $Global:__JetBrainsIntellijState.IsCommandRunning = $false
    if (($ExitCode -eq $null) -or ($ExitCode -eq 0 -and -not $Success)) {
      $ExitCode = if ($Success) { 0 } else { 1 }
    }
    $CurrentDirectory = (Get-Location).Path
    $Result += Global:__JetBrainsIntellijOSC "command_finished;exit_code=$ExitCode;current_directory=$(Global:__JetBrainsIntellijEncode $CurrentDirectory)"
  }

  $Result += Global:__JetBrainsIntellijOSC "prompt_started"
  # It is a hack to restore the state of $Global:? variable
  # So, the original prompt logic will see the real state.
  # The written error won't be shown anywhere thanks to `-ErrorAction ignore`.
  if ($Success -eq $false) {
  	Write-Error "error" -ErrorAction ignore
  }
  $Result += __JetBrainsIntellijOriginalPrompt
  $Result += Global:__JetBrainsIntellijOSC "prompt_finished"

  return $Result
}

function Global:PSConsoleHostReadLine {
  $Command = $Global:__JetBrainsIntellijState.OriginalPSConsoleHostReadLine.Invoke()
  # Do not consider pressing enter without entering the command or pressing Ctrl+C as "command_started"
  if ($Command -ne "") {
    $Global:__JetBrainsIntellijState.IsCommandRunning = $true
    $OSC = Global:__JetBrainsIntellijOSC "command_started;command=$(Global:__JetBrainsIntellijEncode $Command)"
    [Console]::Write($OSC)
  }

  return $Command
}