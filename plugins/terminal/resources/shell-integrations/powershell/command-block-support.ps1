if ([string]::IsNullOrEmpty($Env:INTELLIJ_TERMINAL_COMMAND_BLOCKS)) {
  return
}

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Global:__JetBrainsIntellijEncode([string]$value) {
  -Join ([System.Text.Encoding]::UTF8.GetBytes($value) | ForEach-Object ToString X2)
}

function Global:__JetBrainsIntellijOSC([string]$body) {
  return "$([char]0x1B)]1341;$body`a"
  # ConPTY processes custom OSC asynchronously with regular output.
  # Let's use C1 control codes for OSC to fool ConPTY and output
  # the escape sequence in proper position of the regular output.
  # return "$([char]0x9D)1341;$body$([char]0x9C)"
}

$Global:__JetBrainsIntellijTerminalInitialized=$false

$Global:__JetBrainsIntellijOriginalPrompt = $function:Prompt

function Global:Prompt() {
  $Success = $?
  $OriginalPrompt = $Global:__JetBrainsIntellijOriginalPrompt.Invoke()
  $Result = ""
  $CommandEndMarker = $Env:JETBRAINS_INTELLIJ_COMMAND_END_MARKER
  if ($CommandEndMarker -eq $null) {
    $CommandEndMarker = ""
  }
  if ($__JetBrainsIntellijTerminalInitialized) {
    $ExitCode = "0"
    if ($LASTEXITCODE -ne $null) {
      $ExitCode = $LASTEXITCODE
    }
    if (-not$Success -and $ExitCode -eq "0") {
      $ExitCode = "1"
    }
    $CurrentDirectory = (Get-Location).Path
    if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
      [Console]::WriteLine("command_finished exit_code=$ExitCode, current_directory=$CurrentDirectory")
    }
    $CommandFinishedEvent = Global:__JetBrainsIntellijOSC "command_finished;exit_code=$ExitCode;current_directory=$(__JetBrainsIntellijEncode $CurrentDirectory)"
    $Result = $CommandEndMarker + $CommandFinishedEvent
  }
  else {
    # For some reason there is no error if I delete the history file, just an empty string returned.
    # There can be a check for file existence using Test-Path cmdlet, but if I add it, the prompt is failed to initialize.
    $History = Get-Content -Raw (Get-PSReadlineOption).HistorySavePath
    $HistoryOSC = Global:__JetBrainsIntellijOSC "command_history;history_string=$(__JetBrainsIntellijEncode $History)"

    $Global:__JetBrainsIntellijTerminalInitialized = $true
    if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
      [Console]::WriteLine("initialized")
    }
    $InitializedEvent = Global:__JetBrainsIntellijOSC "initialized"
    $Result = $CommandEndMarker + $HistoryOSC + $InitializedEvent
  }
  return $Result
}

function Global:__JetBrainsIntellij_ClearAllAndMoveCursorToTopLeft() {
  [Console]::Clear()
}

if (Get-Module -Name PSReadLine) {
  $Global:__JetBrainsIntellijOriginalPSConsoleHostReadLine = $function:PSConsoleHostReadLine

  function Global:PSConsoleHostReadLine {
    $OriginalReadLine = $Global:__JetBrainsIntellijOriginalPSConsoleHostReadLine.Invoke()

    $CurrentDirectory = (Get-Location).Path
    if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
      [Console]::WriteLine("command_started $OriginalReadLine")
    }
    $CommandStartedOSC = Global:__JetBrainsIntellijOSC "command_started;command=$(__JetBrainsIntellijEncode $OriginalReadLine);current_directory=$(__JetBrainsIntellijEncode $CurrentDirectory)"
    [Console]::Write($CommandStartedOSC)
    Global:__JetBrainsIntellij_ClearAllAndMoveCursorToTopLeft
    return $OriginalReadLine
  }
}
