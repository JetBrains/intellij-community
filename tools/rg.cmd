:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# rg (ripgrep) wrapper - Unix section
# Downloads and executes ripgrep with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/rg.cmd
# to verify all platform checksums before committing.

set -eu

# ripgrep configuration
export TOOL_NAME="rg"
export TOOL_VERSION="16.0.0"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="54268ab4643c00a290a29b0086066d64b3f56236dd805ec64629a86f9bd29fd0"
export TOOL_CHECKSUM_LINUX_ARM64="0b0bddcfaf42be1f3c56bf929d70defd1a2d96f2577aecbd7029a3734ddb4740"
export TOOL_CHECKSUM_WINDOWS_X64="f418ae83e63046a94a7b5f3a44bf3a169f66e689164856ecee02547131c73931"
export TOOL_CHECKSUM_WINDOWS_ARM64="3302ede09745d33ca2b287bf3c6434c54cb35c3b408a7d3a265939961add5a1a"
export TOOL_CHECKSUM_MACOS_X64="aa9183b0e0bfac03c5818895ad18b75001c402d230063898abfe5a58cd413caa"
export TOOL_CHECKSUM_MACOS_ARM64="5a145656d5067144001bfce6648767fd3254599aa736f1ffdb7fb907760b8ca1"

# Download URLs (GitHub releases through the cache redirector)
export TOOL_URL_LINUX_X64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-unknown-linux-musl.tar.gz"
export TOOL_URL_LINUX_ARM64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-unknown-linux-gnu.tar.gz"
export TOOL_URL_WINDOWS_X64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-pc-windows-msvc.zip"
export TOOL_URL_WINDOWS_ARM64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-pc-windows-msvc.zip"
export TOOL_URL_MACOS_X64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-apple-darwin.tar.gz"
export TOOL_URL_MACOS_ARM64="https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-apple-darwin.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="rg"
export TOOL_BINARY_WINDOWS="rg.exe"

# Prefer a locally installed ripgrep if available (skip downloading),
# unless we are verifying all platform checksums.
if [ "${TOOL_VERIFY_ALL_PLATFORMS:-}" != "1" ] && command -v rg >/dev/null 2>&1; then
  exec rg "$@"
fi

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM rg (ripgrep) wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\rg.cmd
REM to verify all platform checksums before committing.

REM ripgrep configuration
set "TOOL_NAME=rg"
set "TOOL_VERSION=16.0.0"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=54268ab4643c00a290a29b0086066d64b3f56236dd805ec64629a86f9bd29fd0"
set "TOOL_CHECKSUM_LINUX_ARM64=0b0bddcfaf42be1f3c56bf929d70defd1a2d96f2577aecbd7029a3734ddb4740"
set "TOOL_CHECKSUM_WINDOWS_X64=f418ae83e63046a94a7b5f3a44bf3a169f66e689164856ecee02547131c73931"
set "TOOL_CHECKSUM_WINDOWS_ARM64=3302ede09745d33ca2b287bf3c6434c54cb35c3b408a7d3a265939961add5a1a"
set "TOOL_CHECKSUM_MACOS_X64=aa9183b0e0bfac03c5818895ad18b75001c402d230063898abfe5a58cd413caa"
set "TOOL_CHECKSUM_MACOS_ARM64=5a145656d5067144001bfce6648767fd3254599aa736f1ffdb7fb907760b8ca1"

REM Download URLs (GitHub releases through the cache redirector)
set "TOOL_URL_LINUX_X64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-unknown-linux-musl.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-pc-windows-msvc.zip"
set "TOOL_URL_WINDOWS_ARM64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-pc-windows-msvc.zip"
set "TOOL_URL_MACOS_X64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-apple-darwin.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://cache-redirector.jetbrains.com/github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-apple-darwin.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=rg"
set "TOOL_BINARY_WINDOWS=rg.exe"

REM Prefer a locally installed ripgrep if available (skip downloading),
REM unless we are verifying all platform checksums.
if not "%TOOL_VERIFY_ALL_PLATFORMS%"=="1" (
  for /f "delims=" %%i in ('where rg.exe 2^>nul') do (
    "%%i" %*
    exit /B
  )
)

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
