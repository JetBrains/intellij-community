if ([string]::IsNullOrEmpty($Env:INTELLIJ_TERMINAL_COMMAND_BLOCKS)) {
  return
}

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Import PSReadLine module forcefully if it was skipped because of active Screen Reader.
# PowerShell is skipping it when Screen Reader is active because they consider it as not well accessibility friendly.
# PSReadLine module is required for our shell integration. Namely for command_started event and command history filtering.
# PSReadLine module is shipped together PowerShell with from version 5.1.
# Since PowerShell 5.1 is bundled in both Windows 10 and 11, it suits us well and this module must be present.
if ((Get-Module -Name PSReadLine) -eq $null) {
  Import-Module PSReadLine
}

function Global:__JetBrainsIntellijEncode([object]$value) {
  # Value that we need to encode is not always a string.
  # Generator result can be an array of objects, for example, type of the `git config --get-regexp "^alias"` command output is Object[].
  # So, we need to use Out-String cmdlet to transform the Object[] to the string like in the terminal output.
  # Otherwise GetBytes call will transform it in some other way and we will lose the line brakes.
  $ValueAsString = if ($value -is [string]) { $value } else { ($value | Out-String).trim() }
  $Bytes = [System.Text.Encoding]::UTF8.GetBytes($ValueAsString)
  return [System.BitConverter]::ToString($Bytes).Replace("-", "")
}

function Global:__JetBrainsIntellijOSC([string]$body) {
  return "$([char]0x1B)]1341;$body`a"
  # ConPTY processes custom OSC asynchronously with regular output.
  # Let's use C1 control codes for OSC to fool ConPTY and output
  # the escape sequence in proper position of the regular output.
  # return "$([char]0x9D)1341;$body$([char]0x9C)"
}

function Global:__JetBrainsIntellijGetCommandEndMarker() {
  $CommandEndMarker = $Env:JETBRAINS_INTELLIJ_COMMAND_END_MARKER
  if ($CommandEndMarker -eq $null) {
    $CommandEndMarker = ""
  }
  return $CommandEndMarker
}

$Global:__JetBrainsIntellijTerminalInitialized=$false
$Global:__JetBrainsIntellijGeneratorRunning=$false

if (Test-Path Function:\Prompt) {
  Rename-Item Function:\Prompt Global:__JetBrainsIntellijOriginalPrompt
}
else {
  function Global:__JetBrainsIntellijOriginalPrompt() { return "" }
}

function Global:Prompt() {
  $Success = $?
  $ExitCode = $Global:LastExitCode
  $Global:LastExitCode = 0
  if ($Global:__JetBrainsIntellijGeneratorRunning) {
    $Global:__JetBrainsIntellijGeneratorRunning = $false
    # Hide internal command in the built-in session history.
    # See "Set-PSReadLineOption -AddToHistoryHandler" for hiding same commands in the PSReadLine history.
    Clear-History -CommandLine "__jetbrains_intellij_run_generator*"
    return ""
  }

  $Result = ""
  $CommandEndMarker = Global:__JetBrainsIntellijGetCommandEndMarker
  $PromptStateOSC = Global:__JetBrainsIntellijCreatePromptStateOSC
  if ($__JetBrainsIntellijTerminalInitialized) {
    if (($ExitCode -eq $null) -or ($ExitCode -eq 0 -and -not $Success)) {
      $ExitCode = if ($Success) { 0 } else { 1 }
    }
    if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
      [Console]::WriteLine("command_finished exit_code=$ExitCode")
    }
    $CommandFinishedEvent = Global:__JetBrainsIntellijOSC "command_finished;exit_code=$ExitCode"
    $Result = $CommandEndMarker + $PromptStateOSC + $CommandFinishedEvent
  }
  else {
    # For some reason there is no error if I delete the history file, just an empty string returned.
    # There can be a check for file existence using Test-Path cmdlet, but if I add it, the prompt is failed to initialize.
    $History = Get-Content -Raw (Get-PSReadlineOption).HistorySavePath
    $HistoryOSC = Global:__JetBrainsIntellijOSC "command_history;history_string=$(__JetBrainsIntellijEncode $History)"

    $ShellInfo = Global:__JetBrainsIntellijCollectShellInfo
    $Global:__JetBrainsIntellijTerminalInitialized = $true
    if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
      [Console]::WriteLine("initialized")
    }
    $InitializedEvent = Global:__JetBrainsIntellijOSC "initialized;shell_info=$(__JetBrainsIntellijEncode $ShellInfo)"
    $Result = $CommandEndMarker + $PromptStateOSC + $HistoryOSC + $InitializedEvent
  }
  return $Result
}

function Global:__JetBrainsIntellijCreatePromptStateOSC() {
  # Remember the exit code, because it can be changed in a result of git operations
  $RealExitCode = $Global:LastExitCode

  $CurrentDirectory = (Get-Location).Path
  $UserName = if ($Env:UserName -ne $null) { $Env:UserName } else { "" }
  $UserHome = if ($Env:HOME -ne $null) { $Env:HOME } else { "" }
  $GitBranch = ""
  if (Get-Command "git.exe" -ErrorAction SilentlyContinue) {
    $GitBranch = git.exe symbolic-ref --short HEAD 2>$null
    if ($GitBranch -eq $null) {
      # get the current revision hash, if not on the branch
      $GitBranch = git.exe rev-parse --short HEAD 2>$null
      if ($GitBranch -eq $null) {
        $GitBranch = ""
      }
    }
  }
  $VirtualEnv = if ($Env:VIRTUAL_ENV -ne $null) { $Env:VIRTUAL_ENV } else { "" }
  $CondaEnv = if ($Env:CONDA_DEFAULT_ENV -ne $null) { $Env:CONDA_DEFAULT_ENV } else { "" }
  $OriginalPrompt = __JetBrainsIntellijOriginalPrompt 6>&1
  $StateOSC = Global:__JetBrainsIntellijOSC ("prompt_state_updated;" +
    "current_directory=$(__JetBrainsIntellijEncode $CurrentDirectory);" +
    "user_name=$(__JetBrainsIntellijEncode $UserName);" +
    "user_home=$(__JetBrainsIntellijEncode $UserHome);" +
    "git_branch=$(__JetBrainsIntellijEncode $GitBranch);" +
    "virtual_env=$(__JetBrainsIntellijEncode $VirtualEnv);" +
    "conda_env=$(__JetBrainsIntellijEncode $CondaEnv);" +
    "original_prompt=$(__JetBrainsIntellijEncode $OriginalPrompt)")

  $Global:LastExitCode = $RealExitCode
  return $StateOSC
}

function Global:__JetBrainsIntellijCollectShellInfo() {
  $ShellVersion = if ($PSVersionTable -ne $null) { $PSVersionTable.PSVersion.toString() } else { "" }
  $IsStarship = ($Env:STARSHIP_START_TIME -ne $null) -or ($Env:STARSHIP_SHELL -ne $null) -or ($Env:STARSHIP_SESSION_KEY -ne $null)
  $OhMyPoshTheme = ""
  if (($Env:POSH_THEME -ne $null) -or ($Env:POSH_PID -ne $null) -or ($Env:POSH_SHELL_VERSION -ne $null)) {
    $OhMyPoshTheme = if ($Env:POSH_THEME -ne $null) { $Env:POSH_THEME } else { "default" }
  }
  $ShellInfo = [PSCustomObject]@{
    shellVersion = $ShellVersion
    isStarship = $IsStarship
    ohMyPoshTheme = $OhMyPoshTheme
  }
  return $ShellInfo | ConvertTo-Json -Compress
}

function Global:__JetBrainsIntellij_ClearAllAndMoveCursorToTopLeft() {
  [Console]::Clear()
}

function Global:__jetbrains_intellij_run_generator([int]$RequestId, [string]$Command) {
  $Global:__JetBrainsIntellijGeneratorRunning = $true
  # Remember the exit code, because it can be changed in a result of generator command execution
  $RealExitCode = $Global:LastExitCode
  $Global:LastExitCode = 0

  $Success = $false
  $Result = ""
  # Redirect the stderr of generator command to stdout. Invoke-Expression can't take all the output for external applications.
  $AdjustedCommand = $Command + " 2>&1"
  # Catch the exceptions in a different ways, because exceptions
  # inside Invoke-Expression and inside $AdjustedCommand can be propagated differently.
  try {
    $Result = Invoke-Expression $AdjustedCommand -ErrorVariable Exception
    if($Exception -ne $null){
      Throw $Exception
    }
    $Success = $true
  }
  catch {
    $Result = $_
  }
  $ExitCode = $Global:LastExitCode
  if (($ExitCode -eq $null) -or ($ExitCode -eq 0 -and -not $Success)) {
    $ExitCode = if ($Success) { 0 } else { 1 }
  }

  $ResultOSC = Global:__JetBrainsIntellijOSC "generator_finished;request_id=$RequestId;result=$(__JetBrainsIntellijEncode $Result);exit_code=$ExitCode"
  $CommandEndMarker = Global:__JetBrainsIntellijGetCommandEndMarker
  [Console]::Write($CommandEndMarker + $ResultOSC)
  $Global:LastExitCode = $RealExitCode
}

function Global:__JetBrainsIntellijGetCompletions([string]$Command, [int]$CursorIndex) {
  $Completions = TabExpansion2 -inputScript $Command -cursorColumn $CursorIndex
  if ($null -ne $Completions) {
    $CompletionsJson = $Completions | ConvertTo-Json -Compress
  }
  else {
    $CompletionsJson = ""
  }
  return $CompletionsJson
}

function Global:__jetbrains_intellij_get_directory_files([string]$Path) {
  # This setting is effective only in the scope of this function.
  $ErrorActionPreference="Stop"
  $Files = Get-ChildItem -Force -Path $Path | Where { $_ -is [System.IO.FileSystemInfo] }
  $Separator = [System.IO.Path]::DirectorySeparatorChar
  $FileNames = $Files | ForEach-Object { if ($_ -is [System.IO.DirectoryInfo]) { $_.Name + $Separator } else { $_.Name } }
  $FilesString = $FileNames -join "`n"
  return $FilesString
}

function Global:__jetbrains_intellij_get_environment() {
  $Global:__JetBrainsIntellijGeneratorRunning = $true
  $FunctionTypes = @("Function", "Filter", "ExternalScript", "Script")
  $Functions = Get-Command -ListImported -CommandType $FunctionTypes
  $Cmdlets = Get-Command -ListImported -CommandType Cmdlet
  $Commands = Get-Command -ListImported -CommandType Application
  $Aliases = Get-Alias | ForEach-Object { [PSCustomObject]@{ name = $_.Name; definition = $_.Definition } }

  $EnvObject = [PSCustomObject]@{
    envs = ""
    keywords = ""
    builtins = ($Cmdlets | ForEach-Object { $_.Name }) -join "`n"
    functions = ($Functions | ForEach-Object { $_.Name }) -join "`n"
    commands = ($Commands | ForEach-Object { $_.Name }) -join "`n"
    aliases = $Aliases | ConvertTo-Json -Compress
  }
  $EnvJson = $EnvObject | ConvertTo-Json -Compress
  return $EnvJson
}

function Global:__JetBrainsIntellijIsGeneratorCommand([string]$Command) {
  return $Command -like "__jetbrains_intellij_run_generator*"
}

# Override the clear cmdlet to handle it on IDE side and remove the blocks
function Global:Clear-Host() {
  $OSC = Global:__JetBrainsIntellijOSC "clear_invoked"
  [Console]::Write($OSC)
}
function Global:clear() {
  Global:Clear-Host
}

$Global:__JetBrainsIntellijOriginalPSConsoleHostReadLine = $function:PSConsoleHostReadLine

function Global:PSConsoleHostReadLine {
  $OriginalReadLine = $Global:__JetBrainsIntellijOriginalPSConsoleHostReadLine.Invoke()
  if (__JetBrainsIntellijIsGeneratorCommand $OriginalReadLine) {
    return $OriginalReadLine
  }

  $CurrentDirectory = (Get-Location).Path
  if ($Env:JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL) {
    [Console]::WriteLine("command_started $OriginalReadLine")
  }
  $CommandStartedOSC = Global:__JetBrainsIntellijOSC "command_started;command=$(__JetBrainsIntellijEncode $OriginalReadLine);current_directory=$(__JetBrainsIntellijEncode $CurrentDirectory)"
  [Console]::Write($CommandStartedOSC)
  Global:__JetBrainsIntellij_ClearAllAndMoveCursorToTopLeft
  return $OriginalReadLine
}

$Global:__JetBrainsIntellijOriginalAddToHistoryHandler = (Get-PSReadLineOption).AddToHistoryHandler

Set-PSReadLineOption -AddToHistoryHandler {
  param([string]$Command)
  if (__JetBrainsIntellijIsGeneratorCommand $Command) {
    return $false
  }
  if ($Global:__JetBrainsIntellijOriginalAddToHistoryHandler -ne $null) {
    return $Global:__JetBrainsIntellijOriginalAddToHistoryHandler.Invoke($Command)
  }
  return $true
}
