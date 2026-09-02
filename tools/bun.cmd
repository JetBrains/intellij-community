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
export TOOL_VERSION="1.4.0"

# SHA-256 checksums for each platform (Bun v1.4.0)
export TOOL_CHECKSUM_LINUX_X64="2d03fb5fb83ac8b567aca0a281b2ce1a1a19d488f56c2968d88c3f25e92fe452"
export TOOL_CHECKSUM_LINUX_ARM64="4b1a332ee861983eb93bcfe6f770fff94e3e31b2c388bdaea3c8ed35e58eed0e"
export TOOL_CHECKSUM_WINDOWS_X64="e6f093d39da486b20262ca8cdd5ed6a9e8bc9c2f275b78e6d3a0c5b28cc95901"
export TOOL_CHECKSUM_WINDOWS_ARM64="f473bfe2df73ee770548c93dd5d380aea7120c218ec2aa1afdd0bbba7bf18c47"
export TOOL_CHECKSUM_MACOS_X64="1d0211b8f1dc991182344687ad15e72ee86f154845a5f7fa477994cd341dd9b0"
export TOOL_CHECKSUM_MACOS_ARM64="c669e97f6164e1c96e0701748db98dfa77492908cbd8394c7557134a735de381"

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
set "TOOL_VERSION=1.4.0"

REM SHA-256 checksums for each platform (Bun v1.4.0)
set "TOOL_CHECKSUM_LINUX_X64=2d03fb5fb83ac8b567aca0a281b2ce1a1a19d488f56c2968d88c3f25e92fe452"
set "TOOL_CHECKSUM_LINUX_ARM64=4b1a332ee861983eb93bcfe6f770fff94e3e31b2c388bdaea3c8ed35e58eed0e"
set "TOOL_CHECKSUM_WINDOWS_X64=e6f093d39da486b20262ca8cdd5ed6a9e8bc9c2f275b78e6d3a0c5b28cc95901"
set "TOOL_CHECKSUM_WINDOWS_ARM64=f473bfe2df73ee770548c93dd5d380aea7120c218ec2aa1afdd0bbba7bf18c47"
set "TOOL_CHECKSUM_MACOS_X64=1d0211b8f1dc991182344687ad15e72ee86f154845a5f7fa477994cd341dd9b0"
set "TOOL_CHECKSUM_MACOS_ARM64=c669e97f6164e1c96e0701748db98dfa77492908cbd8394c7557134a735de381"

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
