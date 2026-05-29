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
export TOOL_VERSION="1.3.14"

# SHA-256 checksums for each platform (Bun v1.3.14)
export TOOL_CHECKSUM_LINUX_X64="951ee2aee855f08595aeec6225226a298d3fea83a3dcd6465c09cbccdf7e848f"
export TOOL_CHECKSUM_LINUX_ARM64="a27ffb63a8310375836e0d6f668ae17fa8d8d18b88c37c821c65331973a19a3b"
export TOOL_CHECKSUM_WINDOWS_X64="0a0620930b6675d7ba440e81f4e0e00d3cfbe096c4b140d3fff02205e9e18922"
export TOOL_CHECKSUM_WINDOWS_ARM64="89841f5a57f2348b67ec0839b718f4bf4ea7d07c371c9ba4b77b6c790f918953"
export TOOL_CHECKSUM_MACOS_X64="4183df3374623e5bab315c547cfa0974533cd457d86b73b639f7a87974cd6633"
export TOOL_CHECKSUM_MACOS_ARM64="d8b96221828ad6f97ac7ac0ab7e95872341af763001e8803e8267652c2652620"

# Download URLs (GitHub releases)
export TOOL_URL_LINUX_X64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-linux-x64.zip"
export TOOL_URL_LINUX_ARM64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-linux-aarch64.zip"
export TOOL_URL_WINDOWS_X64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-windows-x64.zip"
export TOOL_URL_WINDOWS_ARM64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-windows-aarch64.zip"
export TOOL_URL_MACOS_X64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-darwin-x64.zip"
export TOOL_URL_MACOS_ARM64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-darwin-aarch64.zip"

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
set "TOOL_VERSION=1.3.14"

REM SHA-256 checksums for each platform (Bun v1.3.14)
set "TOOL_CHECKSUM_LINUX_X64=951ee2aee855f08595aeec6225226a298d3fea83a3dcd6465c09cbccdf7e848f"
set "TOOL_CHECKSUM_LINUX_ARM64=a27ffb63a8310375836e0d6f668ae17fa8d8d18b88c37c821c65331973a19a3b"
set "TOOL_CHECKSUM_WINDOWS_X64=0a0620930b6675d7ba440e81f4e0e00d3cfbe096c4b140d3fff02205e9e18922"
set "TOOL_CHECKSUM_WINDOWS_ARM64=89841f5a57f2348b67ec0839b718f4bf4ea7d07c371c9ba4b77b6c790f918953"
set "TOOL_CHECKSUM_MACOS_X64=4183df3374623e5bab315c547cfa0974533cd457d86b73b639f7a87974cd6633"
set "TOOL_CHECKSUM_MACOS_ARM64=d8b96221828ad6f97ac7ac0ab7e95872341af763001e8803e8267652c2652620"

REM Download URLs (GitHub releases)
set "TOOL_URL_LINUX_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-linux-x64.zip"
set "TOOL_URL_LINUX_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-linux-aarch64.zip"
set "TOOL_URL_WINDOWS_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-windows-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-windows-aarch64.zip"
set "TOOL_URL_MACOS_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-darwin-x64.zip"
set "TOOL_URL_MACOS_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-darwin-aarch64.zip"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bun"
set "TOOL_BINARY_WINDOWS=bun.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
