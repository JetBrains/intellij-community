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
export TOOL_VERSION="1.3.11"

# SHA-256 checksums for each platform (Bun v1.3.11)
export TOOL_CHECKSUM_LINUX_X64="8611ba935af886f05a6f38740a15160326c15e5d5d07adef966130b4493607ed"
export TOOL_CHECKSUM_LINUX_ARM64="d13944da12a53ecc74bf6a720bd1d04c4555c038dfe422365356a7be47691fdf"
export TOOL_CHECKSUM_WINDOWS_X64="066f8694f8b7d8df592452746d18f01710d4053e93030922dbc6e8c34a8c4b9f"
export TOOL_CHECKSUM_WINDOWS_ARM64="c7f661d7529ec3f2fdfc1eac39a760c65f526955bce06b74859c532cb4bf00d7"
export TOOL_CHECKSUM_MACOS_X64="c4fe2b9247218b0295f24e895aaec8fee62e74452679a9026b67eacbd611a286"
export TOOL_CHECKSUM_MACOS_ARM64="6f5a3467ed9caec4795bf78cd476507d9f870c7d57b86c945fcb338126772ffc"

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
set "TOOL_VERSION=1.3.11"

REM SHA-256 checksums for each platform (Bun v1.3.11)
set "TOOL_CHECKSUM_LINUX_X64=8611ba935af886f05a6f38740a15160326c15e5d5d07adef966130b4493607ed"
set "TOOL_CHECKSUM_LINUX_ARM64=d13944da12a53ecc74bf6a720bd1d04c4555c038dfe422365356a7be47691fdf"
set "TOOL_CHECKSUM_WINDOWS_X64=066f8694f8b7d8df592452746d18f01710d4053e93030922dbc6e8c34a8c4b9f"
set "TOOL_CHECKSUM_WINDOWS_ARM64=c7f661d7529ec3f2fdfc1eac39a760c65f526955bce06b74859c532cb4bf00d7"
set "TOOL_CHECKSUM_MACOS_X64=c4fe2b9247218b0295f24e895aaec8fee62e74452679a9026b67eacbd611a286"
set "TOOL_CHECKSUM_MACOS_ARM64=6f5a3467ed9caec4795bf78cd476507d9f870c7d57b86c945fcb338126772ffc"

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
