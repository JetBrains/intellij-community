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
export TOOL_VERSION="22.21.1"

# SHA-256 checksums for each platform (Node.js v22.21.1 LTS)
export TOOL_CHECKSUM_LINUX_X64="219a152ea859861d75adea578bdec3dce8143853c13c5187f40c40e77b0143b2"
export TOOL_CHECKSUM_LINUX_ARM64="c86830dedf77f8941faa6c5a9c863bdfdd1927a336a46943decc06a38f80bfb2"
export TOOL_CHECKSUM_WINDOWS_X64="3c624e9fbe07e3217552ec52a0f84e2bdc2e6ffa7348f3fdfb9fbf8f42e23fcf"
export TOOL_CHECKSUM_WINDOWS_ARM64="b9d7faacd0b540b8b46640dbc8f56f4205ff63b79dec700d4f03d36591b0318f"
export TOOL_CHECKSUM_MACOS_X64="8e3dc89614debe66c2a6ad2313a1adb06eb37db6cd6c40d7de6f7d987f7d1afd"
export TOOL_CHECKSUM_MACOS_ARM64="c170d6554fba83d41d25a76cdbad85487c077e51fa73519e41ac885aa429d8af"

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
set "TOOL_VERSION=22.21.1"

REM SHA-256 checksums for each platform (Node.js v22.21.1 LTS)
set "TOOL_CHECKSUM_LINUX_X64=219a152ea859861d75adea578bdec3dce8143853c13c5187f40c40e77b0143b2"
set "TOOL_CHECKSUM_LINUX_ARM64=c86830dedf77f8941faa6c5a9c863bdfdd1927a336a46943decc06a38f80bfb2"
set "TOOL_CHECKSUM_WINDOWS_X64=3c624e9fbe07e3217552ec52a0f84e2bdc2e6ffa7348f3fdfb9fbf8f42e23fcf"
set "TOOL_CHECKSUM_WINDOWS_ARM64=b9d7faacd0b540b8b46640dbc8f56f4205ff63b79dec700d4f03d36591b0318f"
set "TOOL_CHECKSUM_MACOS_X64=8e3dc89614debe66c2a6ad2313a1adb06eb37db6cd6c40d7de6f7d987f7d1afd"
set "TOOL_CHECKSUM_MACOS_ARM64=c170d6554fba83d41d25a76cdbad85487c077e51fa73519e41ac885aa429d8af"

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
