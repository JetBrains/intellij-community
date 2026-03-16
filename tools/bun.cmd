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
export TOOL_VERSION="1.3.7"

# SHA-256 checksums for each platform (Bun v1.3.7)
export TOOL_CHECKSUM_LINUX_X64="2bd2e0e0bdf09483be67a704607848ebe72c28420824e4ce772ce3da62c23d65"
export TOOL_CHECKSUM_LINUX_ARM64="d5c7d651423c2bc5ae3f92d36837ffb2ddc6ee91849672500b7fe8e5a5159fbc"
export TOOL_CHECKSUM_WINDOWS_X64="659af6415800976c40338d5e1b9c0a4d61c503b880656cb00b43865a3a99e3bf"
# NOTE: Bun v1.3.7 does not ship a Windows ARM64 build; use x64 archive.
export TOOL_CHECKSUM_WINDOWS_ARM64="659af6415800976c40338d5e1b9c0a4d61c503b880656cb00b43865a3a99e3bf"
export TOOL_CHECKSUM_MACOS_X64="cdfe9c71cacbdd9a73f098c3b050671957a7414e1321dfedd9a410d7794dae51"
export TOOL_CHECKSUM_MACOS_ARM64="16701e494998e4764d49af2fbe62d25ec59cf3c79ee696eba1def2cfe9049d64"

# Download URLs (GitHub releases)
export TOOL_URL_LINUX_X64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-linux-x64.zip"
export TOOL_URL_LINUX_ARM64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-linux-aarch64.zip"
export TOOL_URL_WINDOWS_X64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-windows-x64.zip"
export TOOL_URL_WINDOWS_ARM64="https://github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}/bun-windows-x64.zip"
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
set "TOOL_VERSION=1.3.7"

REM SHA-256 checksums for each platform (Bun v1.3.7)
set "TOOL_CHECKSUM_LINUX_X64=2bd2e0e0bdf09483be67a704607848ebe72c28420824e4ce772ce3da62c23d65"
set "TOOL_CHECKSUM_LINUX_ARM64=d5c7d651423c2bc5ae3f92d36837ffb2ddc6ee91849672500b7fe8e5a5159fbc"
set "TOOL_CHECKSUM_WINDOWS_X64=659af6415800976c40338d5e1b9c0a4d61c503b880656cb00b43865a3a99e3bf"
REM NOTE: Bun v1.3.7 does not ship a Windows ARM64 build; use x64 archive.
set "TOOL_CHECKSUM_WINDOWS_ARM64=659af6415800976c40338d5e1b9c0a4d61c503b880656cb00b43865a3a99e3bf"
set "TOOL_CHECKSUM_MACOS_X64=cdfe9c71cacbdd9a73f098c3b050671957a7414e1321dfedd9a410d7794dae51"
set "TOOL_CHECKSUM_MACOS_ARM64=16701e494998e4764d49af2fbe62d25ec59cf3c79ee696eba1def2cfe9049d64"

REM Download URLs (GitHub releases)
set "TOOL_URL_LINUX_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-linux-x64.zip"
set "TOOL_URL_LINUX_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-linux-aarch64.zip"
set "TOOL_URL_WINDOWS_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-windows-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-windows-x64.zip"
set "TOOL_URL_MACOS_X64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-darwin-x64.zip"
set "TOOL_URL_MACOS_ARM64=https://github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%/bun-darwin-aarch64.zip"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bun"
set "TOOL_BINARY_WINDOWS=bun.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
