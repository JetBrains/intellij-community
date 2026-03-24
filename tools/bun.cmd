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
export TOOL_VERSION="1.3.10"

# SHA-256 checksums for each platform (Bun v1.3.10)
export TOOL_CHECKSUM_LINUX_X64="f57bc0187e39623de716ba3a389fda5486b2d7be7131a980ba54dc7b733d2e08"
export TOOL_CHECKSUM_LINUX_ARM64="fa5ecb25cafa8e8f5c87a0f833719d46dd0af0a86c7837d806531212d55636d3"
export TOOL_CHECKSUM_WINDOWS_X64="7a77b3e245e2e26965c93089a4a1332e8a326d3364c89fae1d1fd99cdd3cd73d"
export TOOL_CHECKSUM_WINDOWS_ARM64="6822f3aa7bd2be40fb94c194a1185aae1c6fade54ca4fc2efdc722e37f3257d2"
export TOOL_CHECKSUM_MACOS_X64="c1d90bf6140f20e572c473065dc6b37a4b036349b5e9e4133779cc642ad94323"
export TOOL_CHECKSUM_MACOS_ARM64="82034e87c9d9b4398ea619aee2eed5d2a68c8157e9a6ae2d1052d84d533ccd8d"

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
set "TOOL_VERSION=1.3.10"

REM SHA-256 checksums for each platform (Bun v1.3.10)
set "TOOL_CHECKSUM_LINUX_X64=f57bc0187e39623de716ba3a389fda5486b2d7be7131a980ba54dc7b733d2e08"
set "TOOL_CHECKSUM_LINUX_ARM64=fa5ecb25cafa8e8f5c87a0f833719d46dd0af0a86c7837d806531212d55636d3"
set "TOOL_CHECKSUM_WINDOWS_X64=7a77b3e245e2e26965c93089a4a1332e8a326d3364c89fae1d1fd99cdd3cd73d"
set "TOOL_CHECKSUM_WINDOWS_ARM64=6822f3aa7bd2be40fb94c194a1185aae1c6fade54ca4fc2efdc722e37f3257d2"
set "TOOL_CHECKSUM_MACOS_X64=c1d90bf6140f20e572c473065dc6b37a4b036349b5e9e4133779cc642ad94323"
set "TOOL_CHECKSUM_MACOS_ARM64=82034e87c9d9b4398ea619aee2eed5d2a68c8157e9a6ae2d1052d84d533ccd8d"

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
