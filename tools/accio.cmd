:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# accio wrapper - Unix section
# Downloads and executes accio with version pinning and checksum verification.
# The archives are the assets of a GitHub release of JetBrains/accio, mirrored to intellij-build-dependencies.
# The cache redirector cannot read the internal GitHub repository, so the mirror is necessary.
# An accio on the PATH wins over the pin when its version is at or above the pin: a developer build stays the one in use.
# An older one gets one note on stderr, and the pinned accio runs. A cache file holds the version of the accio on the PATH.
# It stays valid while it is newer than the binary and the directory of the binary, so a normal call starts no extra process.
# ACCIO_WRAPPER_ACTIVE stops a loop when that accio is this wrapper.
#
# To bump the pin:
#   1. gh release download v<version> -R JetBrains/accio, then shasum -a 256 -c each .sha256 asset.
#   2. Upload the six archives to accio/<version>-<short sha>/ in https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies.
#      <short sha> is the commit of the tag v<version>.
#   3. Change TOOL_VERSION, ACCIO_ARCHIVE_VERSION and the checksums in both sections. The .sha256 assets hold them.
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/accio.cmd
# to verify all platform checksums before committing.

set -eu

TOOL_VERSION="0.12.2-c6d3cab"
ACCIO_ARCHIVE_VERSION="v0.12.2"

# Exit code 0 when version $1 is at or above version $2 (major.minor.patch). An unparsable version is older.
accio_at_least() {
  accio_a="$1"
  accio_b="$2"
  for _ in 1 2 3; do
    accio_x="${accio_a%%.*}"
    accio_y="${accio_b%%.*}"
    case "$accio_x" in ''|*[!0-9]*) return 1 ;; esac
    case "$accio_y" in ''|*[!0-9]*) return 1 ;; esac
    if [ "$accio_x" -gt "$accio_y" ]; then return 0; fi
    if [ "$accio_x" -lt "$accio_y" ]; then return 1; fi
    case "$accio_a" in *.*) accio_a="${accio_a#*.}" ;; *) accio_a= ;; esac
    case "$accio_b" in *.*) accio_b="${accio_b#*.}" ;; *) accio_b= ;; esac
  done
  return 0
}

if [ -z "${ACCIO_WRAPPER_ACTIVE:-}" ] && accio_local="$(command -v accio 2>/dev/null)"; then
  accio_pin="${TOOL_VERSION%%-*}"
  if [ -d "${HOME:-}/Library/Caches" ]; then
    accio_cache="${HOME:-}/Library/Caches/JetBrains/monorepo-tools/accio/path-version"
  else
    accio_cache="${HOME:-}/.cache/JetBrains/monorepo-tools/accio/path-version"
  fi
  accio_version=
  accio_probed=
  if read -r accio_cached_version accio_cached_path 2>/dev/null < "$accio_cache" \
    && [ "$accio_cached_path" = "$accio_local" ] \
    && [ "$accio_cache" -nt "$accio_local" ] \
    && [ "$accio_cache" -nt "${accio_local%/*}" ]; then
    accio_version="$accio_cached_version"
  else
    accio_probed=1
    accio_version="$(ACCIO_WRAPPER_ACTIVE=1 "$accio_local" --version </dev/null 2>/dev/null)" || accio_version=
    accio_nl='
'
    accio_version="${accio_version%%"$accio_nl"*}"
    case "$accio_version" in
      "accio "?*) accio_version="${accio_version#accio }"; accio_version="${accio_version%% *}"; accio_version="${accio_version%%-*}" ;;
      *) accio_version= ;;
    esac
    if [ -z "$accio_version" ]; then accio_version=none; fi
    { mkdir -p "${accio_cache%/*}" && printf '%s %s\n' "$accio_version" "$accio_local" > "$accio_cache.$$" \
      && mv -f "$accio_cache.$$" "$accio_cache"; } 2>/dev/null || rm -f "$accio_cache.$$" 2>/dev/null || true
  fi
  if accio_at_least "$accio_version" "$accio_pin"; then
    export ACCIO_WRAPPER_ACTIVE=1
    exec "$accio_local" "$@"
  fi
  if [ -n "$accio_probed" ]; then
    echo "accio: $accio_local is $accio_version, older than the pin $accio_pin; the pinned accio runs (cache $accio_cache)" >&2
  fi
fi

# accio configuration
export TOOL_NAME="accio"
export TOOL_VERSION

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="750e8ceb6248473ef2ea64c17782a8eaaa1e39db9b3b1e5861677d474e1c98cb"
export TOOL_CHECKSUM_LINUX_ARM64="c4a9067a2688446464edd7b084e415d38211d09682c74c168fba0478cc1c24fc"
export TOOL_CHECKSUM_WINDOWS_X64="6e5f5aad3be61f0c022c8101f70776f71720ee88ae5bf4f4c46338cf34bacfbf"
export TOOL_CHECKSUM_WINDOWS_ARM64="f16b39a1365e2473dd42371ce8e206abf95640c124e6306299ff17af87b0cd66"
export TOOL_CHECKSUM_MACOS_X64="e8f6f3ad65ad390874c55702cafa3589e52ce14342b28bd29a254d908ff88c2d"
export TOOL_CHECKSUM_MACOS_ARM64="a1b28549cc83753b11cd2adc4eb2101c3312006c36ec4efa2e2db93cd2183d12"

# Download URLs (intellij-build-dependencies through the cache redirector)
base="https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/accio/${TOOL_VERSION}/accio-${ACCIO_ARCHIVE_VERSION}"
export TOOL_URL_LINUX_X64="${base}-linux-x86_64.tar.gz"
export TOOL_URL_LINUX_ARM64="${base}-linux-aarch64.tar.gz"
export TOOL_URL_WINDOWS_X64="${base}-windows-x86_64.zip"
export TOOL_URL_WINDOWS_ARM64="${base}-windows-aarch64.zip"
export TOOL_URL_MACOS_X64="${base}-macos-x86_64.tar.gz"
export TOOL_URL_MACOS_ARM64="${base}-macos-aarch64.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="accio"
export TOOL_BINARY_WINDOWS="accio.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM accio wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\accio.cmd
REM to verify all platform checksums before committing.

set "TOOL_VERSION=0.12.2-c6d3cab"
set "ACCIO_ARCHIVE_VERSION=v0.12.2"

REM An accio.exe on the PATH wins over the pin when its version is at or above the pin.
REM A cache file holds its version by path, size and time, so a normal call starts no extra process.
REM Only cmd builtins and goto: a ( ) block breaks on a path with (x86).
if defined ACCIO_WRAPPER_ACTIVE goto :ACCIO_PINNED
set "ACCIO_WRAPPER_ACTIVE=1"
set "ACCIO_LOCAL="
for %%I in (accio.exe) do set "ACCIO_LOCAL=%%~$PATH:I"
if not defined ACCIO_LOCAL goto :ACCIO_PINNED
for %%F in ("%ACCIO_LOCAL%") do set "ACCIO_STAMP=%%~zF;%%~tF"
set "ACCIO_CACHE="
if defined LOCALAPPDATA set "ACCIO_CACHE=%LOCALAPPDATA%\JetBrains\monorepo-tools\accio\path-version"
set "ACCIO_VERSION="
set "ACCIO_PROBED="
if not defined ACCIO_CACHE goto :ACCIO_PROBE
if not exist "%ACCIO_CACHE%" goto :ACCIO_PROBE
for /f "usebackq tokens=1-3* delims=;" %%a in ("%ACCIO_CACHE%") do if "%%d"=="%ACCIO_LOCAL%" if "%%b;%%c"=="%ACCIO_STAMP%" set "ACCIO_VERSION=%%a"
if defined ACCIO_VERSION goto :ACCIO_COMPARE

:ACCIO_PROBE
set "ACCIO_PROBED=1"
set "ACCIO_VERSION=none"
for /f "tokens=1,2" %%a in ('call "%ACCIO_LOCAL%" --version ^<nul 2^>nul') do if "%%a"=="accio" set "ACCIO_VERSION=%%b"
if not defined ACCIO_CACHE goto :ACCIO_COMPARE
for %%D in ("%ACCIO_CACHE%") do if not exist "%%~dpD" mkdir "%%~dpD" 2>nul
2>nul >"%ACCIO_CACHE%.tmp" echo %ACCIO_VERSION%;%ACCIO_STAMP%;%ACCIO_LOCAL%
move /y "%ACCIO_CACHE%.tmp" "%ACCIO_CACHE%" >nul 2>nul

:ACCIO_COMPARE
REM Compare major.minor.patch. An unparsable version is older.
set "ACCIO_A1="
set "ACCIO_A2="
set "ACCIO_A3="
for /f "tokens=1-3 delims=.-" %%a in ("%ACCIO_VERSION%") do set "ACCIO_A1=%%a" & set "ACCIO_A2=%%b" & set "ACCIO_A3=%%c"
for /f "tokens=1-3 delims=.-" %%a in ("%TOOL_VERSION%") do set "ACCIO_B1=%%a" & set "ACCIO_B2=%%b" & set "ACCIO_B3=%%c"
if not defined ACCIO_A3 goto :ACCIO_OLDER
for /f "delims=0123456789" %%c in ("%ACCIO_A1%%ACCIO_A2%%ACCIO_A3%x") do if not "%%c"=="x" goto :ACCIO_OLDER
if %ACCIO_A1% GTR %ACCIO_B1% goto :ACCIO_RUN_LOCAL
if %ACCIO_A1% LSS %ACCIO_B1% goto :ACCIO_OLDER
if %ACCIO_A2% GTR %ACCIO_B2% goto :ACCIO_RUN_LOCAL
if %ACCIO_A2% LSS %ACCIO_B2% goto :ACCIO_OLDER
if %ACCIO_A3% LSS %ACCIO_B3% goto :ACCIO_OLDER

:ACCIO_RUN_LOCAL
"%ACCIO_LOCAL%" %*
exit /B %ERRORLEVEL%

:ACCIO_OLDER
if defined ACCIO_PROBED echo accio: %ACCIO_LOCAL% is %ACCIO_VERSION%, older than the pin %ACCIO_B1%.%ACCIO_B2%.%ACCIO_B3%; the pinned accio runs (cache %ACCIO_CACHE%) 1>&2
:ACCIO_PINNED

REM accio configuration
set "TOOL_NAME=accio"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=750e8ceb6248473ef2ea64c17782a8eaaa1e39db9b3b1e5861677d474e1c98cb"
set "TOOL_CHECKSUM_LINUX_ARM64=c4a9067a2688446464edd7b084e415d38211d09682c74c168fba0478cc1c24fc"
set "TOOL_CHECKSUM_WINDOWS_X64=6e5f5aad3be61f0c022c8101f70776f71720ee88ae5bf4f4c46338cf34bacfbf"
set "TOOL_CHECKSUM_WINDOWS_ARM64=f16b39a1365e2473dd42371ce8e206abf95640c124e6306299ff17af87b0cd66"
set "TOOL_CHECKSUM_MACOS_X64=e8f6f3ad65ad390874c55702cafa3589e52ce14342b28bd29a254d908ff88c2d"
set "TOOL_CHECKSUM_MACOS_ARM64=a1b28549cc83753b11cd2adc4eb2101c3312006c36ec4efa2e2db93cd2183d12"

REM Download URLs (intellij-build-dependencies through the cache redirector)
set "ACCIO_BASE_URL=https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/accio/%TOOL_VERSION%/accio-%ACCIO_ARCHIVE_VERSION%"
set "TOOL_URL_LINUX_X64=%ACCIO_BASE_URL%-linux-x86_64.tar.gz"
set "TOOL_URL_LINUX_ARM64=%ACCIO_BASE_URL%-linux-aarch64.tar.gz"
set "TOOL_URL_WINDOWS_X64=%ACCIO_BASE_URL%-windows-x86_64.zip"
set "TOOL_URL_WINDOWS_ARM64=%ACCIO_BASE_URL%-windows-aarch64.zip"
set "TOOL_URL_MACOS_X64=%ACCIO_BASE_URL%-macos-x86_64.tar.gz"
set "TOOL_URL_MACOS_ARM64=%ACCIO_BASE_URL%-macos-aarch64.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=accio"
set "TOOL_BINARY_WINDOWS=accio.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
