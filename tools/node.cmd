:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.
# IMPORTANT: node.cmd and npx.cmd share the same Node.js installation.
#            Update BOTH files simultaneously with identical TOOL_VERSION and checksums.

# node wrapper - Unix section
# Downloads Node.js and executes node with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/node.cmd
# to verify all platform checksums before committing.

set -eu

# Node.js configuration
export TOOL_NAME="node"
export TOOL_VERSION="24.14.1"

# SHA-256 checksums for each platform (Node.js v24.14.1 LTS)
export TOOL_CHECKSUM_LINUX_X64="ace9fa104992ed0829642629c46ca7bd7fd6e76278cb96c958c4b387d29658ea"
export TOOL_CHECKSUM_LINUX_ARM64="734ff04fa7f8ed2e8a78d40cacf5ac3fc4515dac2858757cbab313eb483ba8a2"
export TOOL_CHECKSUM_WINDOWS_X64="6e50ce5498c0cebc20fd39ab3ff5df836ed2f8a31aa093cecad8497cff126d70"
export TOOL_CHECKSUM_WINDOWS_ARM64="a7b7c68490e4a8cde1921fe5a0cfb3001d53f9c839e416903e4f28e727b62f60"
export TOOL_CHECKSUM_MACOS_X64="2526230ad7d922be82d4fdb1e7ee1e84303e133e3b4b0ec4c2897ab31de0253d"
export TOOL_CHECKSUM_MACOS_ARM64="25495ff85bd89e2d8a24d88566d7e2f827c6b0d3d872b2cebf75371f93fcb1fe"

# Download URLs (nodejs.org official releases)
export TOOL_URL_LINUX_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-linux-x64.tar.gz"
export TOOL_URL_LINUX_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-linux-arm64.tar.gz"
export TOOL_URL_WINDOWS_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-win-x64.zip"
export TOOL_URL_WINDOWS_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-win-arm64.zip"
export TOOL_URL_MACOS_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-darwin-x64.tar.gz"
export TOOL_URL_MACOS_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-darwin-arm64.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="bin/node"
export TOOL_BINARY_WINDOWS="node.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.
REM IMPORTANT: node.cmd and npx.cmd share the same Node.js installation.
REM            Update BOTH files simultaneously with identical TOOL_VERSION and checksums.

REM node wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\node.cmd
REM to verify all platform checksums before committing.

REM Node.js configuration
set "TOOL_NAME=node"
set "TOOL_VERSION=24.14.1"

REM SHA-256 checksums for each platform (Node.js v24.14.1 LTS)
set "TOOL_CHECKSUM_LINUX_X64=ace9fa104992ed0829642629c46ca7bd7fd6e76278cb96c958c4b387d29658ea"
set "TOOL_CHECKSUM_LINUX_ARM64=734ff04fa7f8ed2e8a78d40cacf5ac3fc4515dac2858757cbab313eb483ba8a2"
set "TOOL_CHECKSUM_WINDOWS_X64=6e50ce5498c0cebc20fd39ab3ff5df836ed2f8a31aa093cecad8497cff126d70"
set "TOOL_CHECKSUM_WINDOWS_ARM64=a7b7c68490e4a8cde1921fe5a0cfb3001d53f9c839e416903e4f28e727b62f60"
set "TOOL_CHECKSUM_MACOS_X64=2526230ad7d922be82d4fdb1e7ee1e84303e133e3b4b0ec4c2897ab31de0253d"
set "TOOL_CHECKSUM_MACOS_ARM64=25495ff85bd89e2d8a24d88566d7e2f827c6b0d3d872b2cebf75371f93fcb1fe"

REM Download URLs (nodejs.org official releases)
set "TOOL_URL_LINUX_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-linux-x64.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-linux-arm64.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-win-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-win-arm64.zip"
set "TOOL_URL_MACOS_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-darwin-x64.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-darwin-arm64.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bin/node"
set "TOOL_BINARY_WINDOWS=node.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
