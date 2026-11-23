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
export TOOL_VERSION="15.2.0"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c"
export TOOL_CHECKSUM_LINUX_ARM64="a740b91c82eaf9914cfedd353572f2791cbe0162c84101ee0951058f4dcbc90d"
export TOOL_CHECKSUM_WINDOWS_X64="71b2fef860abe467217a538ff31de02f5258807c0129f771846f87bd029aafc5"
export TOOL_CHECKSUM_WINDOWS_ARM64="e4abca10c3a64ebea742667dd7009449d49403db5460dd6873e389fa2945360f"
export TOOL_CHECKSUM_MACOS_X64="af7825fcc69a2afc7a7aea55fc9af90e26421d8f20fe59df32e233c0b8a231c1"
export TOOL_CHECKSUM_MACOS_ARM64="3750b2e93f37e0c692657da574d7019a101c0084da05a790c83fd335bad973e4"

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
set "TOOL_VERSION=15.2.0"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=33e15bcf1624b25cdd2a55813a47a2f95dbe126268203e76aa6a585d1e7b149c"
set "TOOL_CHECKSUM_LINUX_ARM64=a740b91c82eaf9914cfedd353572f2791cbe0162c84101ee0951058f4dcbc90d"
set "TOOL_CHECKSUM_WINDOWS_X64=71b2fef860abe467217a538ff31de02f5258807c0129f771846f87bd029aafc5"
set "TOOL_CHECKSUM_WINDOWS_ARM64=e4abca10c3a64ebea742667dd7009449d49403db5460dd6873e389fa2945360f"
set "TOOL_CHECKSUM_MACOS_X64=af7825fcc69a2afc7a7aea55fc9af90e26421d8f20fe59df32e233c0b8a231c1"
set "TOOL_CHECKSUM_MACOS_ARM64=3750b2e93f37e0c692657da574d7019a101c0084da05a790c83fd335bad973e4"

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
