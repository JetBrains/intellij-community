:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# bun wrapper - Unix section
# Downloads and executes bun with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/bun.cmd
# to verify all platform checksums before committing.

set -eu

# bun configuration
export TOOL_NAME="bun"
export TOOL_VERSION="1.4.3"

# SHA-256 checksums for each platform (Bun v1.4.3)
export TOOL_CHECKSUM_LINUX_X64="3e730528d89775f03f87f05c06787c4df4099d0a37f3f252e7b829cb23816e18"
export TOOL_CHECKSUM_LINUX_ARM64="efa9813da5ed72423bf847f916e8d2c47c0d776add972354026a75e10da9aa21"
export TOOL_CHECKSUM_WINDOWS_X64="0bf84e808552beea67bf1202a287a35c6c825d837b287b3f8e9dd513dba35d3d"
export TOOL_CHECKSUM_WINDOWS_ARM64="5e8f142ef722cbc9f67dc068a166e46ee72be8307b38cac7186589d2face668f"
export TOOL_CHECKSUM_MACOS_X64="62660e6438eb872278877acca77e1152d5d031324df368d4a81b428211489d2d"
export TOOL_CHECKSUM_MACOS_ARM64="80afd4c069b46a6fa8f3ec520bebc750a7843e4a8d5b9583d29ad436c00a3403"

# Download URLs: the JetBrains mirror of the GitHub release archives, byte-identical. The same
# mirror serves the bun_* http_archives in the ultimate MODULE.bazel, so both pins share one source.
TOOL_MIRROR="https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/bun/${TOOL_VERSION}"
export TOOL_URL_LINUX_X64="${TOOL_MIRROR}/bun-linux-x64.zip"
export TOOL_URL_LINUX_ARM64="${TOOL_MIRROR}/bun-linux-aarch64.zip"
export TOOL_URL_WINDOWS_X64="${TOOL_MIRROR}/bun-windows-x64.zip"
export TOOL_URL_WINDOWS_ARM64="${TOOL_MIRROR}/bun-windows-aarch64.zip"
export TOOL_URL_MACOS_X64="${TOOL_MIRROR}/bun-darwin-x64.zip"
export TOOL_URL_MACOS_ARM64="${TOOL_MIRROR}/bun-darwin-aarch64.zip"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="bun"
export TOOL_BINARY_WINDOWS="bun.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM bun wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\bun.cmd
REM to verify all platform checksums before committing.

REM bun configuration
set "TOOL_NAME=bun"
set "TOOL_VERSION=1.4.3"

REM SHA-256 checksums for each platform (Bun v1.4.3)
set "TOOL_CHECKSUM_LINUX_X64=3e730528d89775f03f87f05c06787c4df4099d0a37f3f252e7b829cb23816e18"
set "TOOL_CHECKSUM_LINUX_ARM64=efa9813da5ed72423bf847f916e8d2c47c0d776add972354026a75e10da9aa21"
set "TOOL_CHECKSUM_WINDOWS_X64=0bf84e808552beea67bf1202a287a35c6c825d837b287b3f8e9dd513dba35d3d"
set "TOOL_CHECKSUM_WINDOWS_ARM64=5e8f142ef722cbc9f67dc068a166e46ee72be8307b38cac7186589d2face668f"
set "TOOL_CHECKSUM_MACOS_X64=62660e6438eb872278877acca77e1152d5d031324df368d4a81b428211489d2d"
set "TOOL_CHECKSUM_MACOS_ARM64=80afd4c069b46a6fa8f3ec520bebc750a7843e4a8d5b9583d29ad436c00a3403"

REM Download URLs: the JetBrains mirror of the GitHub release archives, byte-identical. The same
REM mirror serves the bun_* http_archives in the ultimate MODULE.bazel, so both pins share one source.
set "TOOL_MIRROR=https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/bun/%TOOL_VERSION%"
set "TOOL_URL_LINUX_X64=%TOOL_MIRROR%/bun-linux-x64.zip"
set "TOOL_URL_LINUX_ARM64=%TOOL_MIRROR%/bun-linux-aarch64.zip"
set "TOOL_URL_WINDOWS_X64=%TOOL_MIRROR%/bun-windows-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=%TOOL_MIRROR%/bun-windows-aarch64.zip"
set "TOOL_URL_MACOS_X64=%TOOL_MIRROR%/bun-darwin-x64.zip"
set "TOOL_URL_MACOS_ARM64=%TOOL_MIRROR%/bun-darwin-aarch64.zip"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bun"
set "TOOL_BINARY_WINDOWS=bun.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
