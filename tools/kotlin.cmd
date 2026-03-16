:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# kotlin.cmd — self-bootstrapping Kotlin script runner
# Downloads kotlin-compiler on first use (no pre-installed dependencies required).
# Java is bootstrapped via community/build/java.cmd which auto-downloads JDK 21.
#
# Usage: ./community/tools/kotlin.cmd path/to/script.main.kts [args...]

set -eu

KOTLIN_VERSION="2.3.10"
KOTLIN_URL="https://github.com/JetBrains/kotlin/releases/download/v${KOTLIN_VERSION}/kotlin-compiler-${KOTLIN_VERSION}.zip"
KOTLIN_CHECKSUM="c8d546f9ff433b529fb0ad43feceb39831040cae2ca8d17e7df46364368c9a9e"

SCRIPT_DIR="$(cd "$(dirname "$0")"; pwd)"
JAVA_CMD="$SCRIPT_DIR/../build/java.cmd"

die() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "$*" >&2
}

compute_checksum() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | cut -d' ' -f1
  else
    die "No SHA-256 tool found (need sha256sum or shasum)"
  fi
}

# Cache directory
case "$(uname -s)" in
  Linux)  CACHE_BASE="${HOME}/.cache/JetBrains/monorepo-tools" ;;
  Darwin) CACHE_BASE="${HOME}/Library/Caches/JetBrains/monorepo-tools" ;;
  *)      die "Unsupported OS: $(uname -s)" ;;
esac

KOTLIN_HOME="$CACHE_BASE/kotlin/$KOTLIN_VERSION"
FLAG_FILE="$KOTLIN_HOME/.complete"

ensure_kotlin_cached() {
  # Fast path: already cached
  if [ -f "$FLAG_FILE" ] && grep -qx "$KOTLIN_CHECKSUM" "$FLAG_FILE" 2>/dev/null; then
    if [ -f "$KOTLIN_HOME/lib/kotlin-compiler.jar" ]; then
      return 0
    fi
  fi

  local lock_dir="$CACHE_BASE/kotlin"
  local lock_file="$lock_dir/.kotlin-cmd-lock.pid"
  local tmp_lock_file="$lock_dir/.tmp.$$.pid"

  mkdir -p "$lock_dir"
  echo $$ > "$tmp_lock_file"

  while ! ln "$tmp_lock_file" "$lock_file" 2>/dev/null; do
    local lock_owner
    lock_owner=$(cat "$lock_file" 2>/dev/null || true)

    while [ -n "$lock_owner" ] && ps -p "$lock_owner" >/dev/null 2>&1; do
      info "Waiting for process $lock_owner to finish downloading Kotlin..."
      sleep 1
      lock_owner=$(cat "$lock_file" 2>/dev/null || true)

      if [ -f "$FLAG_FILE" ] && grep -qx "$KOTLIN_CHECKSUM" "$FLAG_FILE" 2>/dev/null; then
        rm -f "$tmp_lock_file"
        return 0
      fi
    done

    # Stale lock
    if [ -n "$lock_owner" ]; then
      info "Removing stale lock from process $lock_owner"
      rm -f "$lock_file"
    fi
  done

  rm -f "$tmp_lock_file"
  trap "rm -f \"$lock_file\"" EXIT

  # Double-check after acquiring lock
  if [ -f "$FLAG_FILE" ] && grep -qx "$KOTLIN_CHECKSUM" "$FLAG_FILE" 2>/dev/null; then
    if [ -f "$KOTLIN_HOME/lib/kotlin-compiler.jar" ]; then
      rm -f "$lock_file"
      trap - EXIT
      return 0
    fi
  fi

  local temp_dir
  temp_dir=$(mktemp -d)
  local temp_archive="$temp_dir/kotlin-compiler.zip"

  info "Downloading Kotlin $KOTLIN_VERSION..."
  if ! curl -fsSL -o "$temp_archive" "$KOTLIN_URL"; then
    rm -rf "$temp_dir"
    die "Failed to download $KOTLIN_URL"
  fi

  local actual_checksum
  actual_checksum=$(compute_checksum "$temp_archive")
  if [ "$actual_checksum" != "$KOTLIN_CHECKSUM" ]; then
    rm -rf "$temp_dir"
    die "Checksum mismatch for kotlin-compiler-${KOTLIN_VERSION}.zip
Expected: $KOTLIN_CHECKSUM
Actual:   $actual_checksum"
  fi

  info "Checksum verified: $actual_checksum"

  # Extract with top-level kotlinc/ stripped
  rm -rf "$KOTLIN_HOME"
  mkdir -p "$KOTLIN_HOME"

  local temp_extract="$temp_dir/extract"
  mkdir -p "$temp_extract"
  unzip -q "$temp_archive" -d "$temp_extract"

  # Move kotlinc/* contents up into KOTLIN_HOME
  mv "$temp_extract"/kotlinc/* "$KOTLIN_HOME"/
  rm -rf "$temp_dir"

  if [ ! -f "$KOTLIN_HOME/lib/kotlin-compiler.jar" ]; then
    die "kotlin-compiler.jar not found after extraction"
  fi

  echo "$KOTLIN_CHECKSUM" > "$FLAG_FILE"

  rm -f "$lock_file"
  trap - EXIT

  info "Cached Kotlin $KOTLIN_VERSION at $KOTLIN_HOME"
}

ensure_kotlin_cached

exec "$JAVA_CMD" \
  -Dfile.encoding=UTF-8 \
  -cp "$KOTLIN_HOME/lib/kotlin-compiler.jar" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -kotlin-home "$KOTLIN_HOME" \
  -script "$@"


:CMDSCRIPT

setlocal

set KOTLIN_VERSION=2.3.10
set KOTLIN_URL=https://github.com/JetBrains/kotlin/releases/download/v%KOTLIN_VERSION%/kotlin-compiler-%KOTLIN_VERSION%.zip
set KOTLIN_CHECKSUM=c8d546f9ff433b529fb0ad43feceb39831040cae2ca8d17e7df46364368c9a9e

set JAVA_CMD=%~dp0..\build\java.cmd
set POWERSHELL=%SystemRoot%\system32\WindowsPowerShell\v1.0\powershell.exe

if "%TEAMCITY_PERSISTENT_CACHE%" == "" (
  set CACHE_BASE=%LOCALAPPDATA%\JetBrains\monorepo-tools
) else (
  set CACHE_BASE=%TEAMCITY_PERSISTENT_CACHE%
)

set KOTLIN_HOME=%CACHE_BASE%\kotlin\%KOTLIN_VERSION%
set FLAG_FILE=%KOTLIN_HOME%\.complete

if not exist "%FLAG_FILE%" goto downloadKotlin
if not exist "%KOTLIN_HOME%\lib\kotlin-compiler.jar" goto downloadKotlin

set /p CURRENT_FLAG=<"%FLAG_FILE%"
if "%CURRENT_FLAG%" == "%KOTLIN_CHECKSUM%" goto executeKotlin

:downloadKotlin

set KOTLIN_TEMP_FILE=%CACHE_BASE%\kotlin\kotlin-temp.zip

set DOWNLOAD_KOTLIN_PS1= ^
Set-StrictMode -Version 3.0; ^
$ErrorActionPreference = 'Stop'; ^
 ^
$createdNew = $false; ^
$lock = New-Object System.Threading.Mutex($true, 'Global\kotlin-cmd-lock', [ref]$createdNew); ^
if (-not $createdNew) { ^
    Write-Host 'Waiting for another process to finish downloading Kotlin...'; ^
    [void]$lock.WaitOne(); ^
} ^
 ^
try { ^
    $flagContent = Get-Content '%FLAG_FILE%' -ErrorAction Ignore; ^
    if ($flagContent -ne '%KOTLIN_CHECKSUM%' -or -not (Test-Path '%KOTLIN_HOME%\lib\kotlin-compiler.jar')) { ^
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        Write-Host 'Downloading Kotlin %KOTLIN_VERSION%...'; ^
        [void](New-Item '%CACHE_BASE%\kotlin' -ItemType Directory -Force); ^
        (New-Object Net.WebClient).DownloadFile('%KOTLIN_URL%', '%KOTLIN_TEMP_FILE%'); ^
 ^
        $hash = (Get-FileHash '%KOTLIN_TEMP_FILE%' -Algorithm SHA256).Hash.ToLower(); ^
        if ($hash -ne '%KOTLIN_CHECKSUM%') { ^
            Remove-Item '%KOTLIN_TEMP_FILE%' -Force; ^
            throw "Checksum mismatch: expected %KOTLIN_CHECKSUM%, got $hash"; ^
        } ^
        Write-Host "Checksum verified: $hash"; ^
 ^
        if (Test-Path '%KOTLIN_HOME%') { ^
            Remove-Item '%KOTLIN_HOME%' -Recurse -Force; ^
        } ^
        [void](New-Item '%KOTLIN_HOME%' -ItemType Directory -Force); ^
 ^
        Add-Type -A 'System.IO.Compression.FileSystem'; ^
        $tempExtract = '%CACHE_BASE%\kotlin\kotlin-extract-tmp'; ^
        if (Test-Path $tempExtract) { Remove-Item $tempExtract -Recurse -Force; } ^
        [IO.Compression.ZipFile]::ExtractToDirectory('%KOTLIN_TEMP_FILE%', $tempExtract); ^
        Get-ChildItem "$tempExtract\kotlinc\*" | Move-Item -Destination '%KOTLIN_HOME%'; ^
        Remove-Item $tempExtract -Recurse -Force; ^
        Remove-Item '%KOTLIN_TEMP_FILE%' -Force; ^
 ^
        Set-Content '%FLAG_FILE%' -Value '%KOTLIN_CHECKSUM%'; ^
        Write-Host 'Cached Kotlin %KOTLIN_VERSION% at %KOTLIN_HOME%'; ^
    } ^
} ^
finally { ^
    $lock.ReleaseMutex(); ^
}

"%POWERSHELL%" -nologo -noprofile -Command %DOWNLOAD_KOTLIN_PS1% 1>&2
if errorlevel 1 goto fail

:executeKotlin

call "%JAVA_CMD%" -Dfile.encoding=UTF-8 -cp "%KOTLIN_HOME%\lib\kotlin-compiler.jar" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -kotlin-home "%KOTLIN_HOME%" -script %*
exit /B %ERRORLEVEL%

:fail
echo FAIL 1>&2
exit /b 1