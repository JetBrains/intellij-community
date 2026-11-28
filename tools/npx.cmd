:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.
# IMPORTANT: node.cmd and npx.cmd share the same Node.js installation.
#            Update BOTH files simultaneously with identical TOOL_VERSION and checksums.

# npx wrapper - Unix section
# Downloads Node.js and executes npx with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/npx.cmd
# to verify all platform checksums before committing.

set -eu

# Node.js configuration (npx is included with Node.js)
export TOOL_NAME="node"
export TOOL_VERSION="26.13.1"

# SHA-256 checksums for each platform (Node.js v26.13.1 LTS)
export TOOL_CHECKSUM_LINUX_X64="518674de2b621728a5e4486f5fb3b7d2900bb3cc4aa97969edaa463db99f9778"
export TOOL_CHECKSUM_LINUX_ARM64="80a330b35415382878df18d41afbd106ea89bfb3be0087d6608e289a14c5cc7d"
export TOOL_CHECKSUM_WINDOWS_X64="e2edda75bfb089654705cf65a13b58933bfd5f57148e8ebda916f58a2474f1f7"
export TOOL_CHECKSUM_WINDOWS_ARM64="181ebe431b90787113a9e4fc80d38ce86d37888a353a0386de44d2872e48cb97"
export TOOL_CHECKSUM_MACOS_X64="82bb96078a24ca50b9c9a38f2379e6be6d87171fcd400fc14c8934b3bce96e2f"
export TOOL_CHECKSUM_MACOS_ARM64="aec2514ea1ceb2ba52455e9ec65986f6749a06dd759e5db2a72e6889668c9735"

# Download URLs (nodejs.org official releases)
export TOOL_URL_LINUX_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-linux-x64.tar.gz"
export TOOL_URL_LINUX_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-linux-arm64.tar.gz"
export TOOL_URL_WINDOWS_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-win-x64.zip"
export TOOL_URL_WINDOWS_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-win-arm64.zip"
export TOOL_URL_MACOS_X64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-darwin-x64.tar.gz"
export TOOL_URL_MACOS_ARM64="https://nodejs.org/dist/v${TOOL_VERSION}/node-v${TOOL_VERSION}-darwin-arm64.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="bin/npx"
export TOOL_BINARY_WINDOWS="npx.cmd"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.
REM IMPORTANT: node.cmd and npx.cmd share the same Node.js installation.
REM            Update BOTH files simultaneously with identical TOOL_VERSION and checksums.

REM npx wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\npx.cmd
REM to verify all platform checksums before committing.

REM Node.js configuration (npx is included with Node.js)
set "TOOL_NAME=node"
set "TOOL_VERSION=26.13.1"

REM SHA-256 checksums for each platform (Node.js v26.13.1 LTS)
set "TOOL_CHECKSUM_LINUX_X64=518674de2b621728a5e4486f5fb3b7d2900bb3cc4aa97969edaa463db99f9778"
set "TOOL_CHECKSUM_LINUX_ARM64=80a330b35415382878df18d41afbd106ea89bfb3be0087d6608e289a14c5cc7d"
set "TOOL_CHECKSUM_WINDOWS_X64=e2edda75bfb089654705cf65a13b58933bfd5f57148e8ebda916f58a2474f1f7"
set "TOOL_CHECKSUM_WINDOWS_ARM64=181ebe431b90787113a9e4fc80d38ce86d37888a353a0386de44d2872e48cb97"
set "TOOL_CHECKSUM_MACOS_X64=82bb96078a24ca50b9c9a38f2379e6be6d87171fcd400fc14c8934b3bce96e2f"
set "TOOL_CHECKSUM_MACOS_ARM64=aec2514ea1ceb2ba52455e9ec65986f6749a06dd759e5db2a72e6889668c9735"

REM Download URLs (nodejs.org official releases)
set "TOOL_URL_LINUX_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-linux-x64.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-linux-arm64.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-win-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-win-arm64.zip"
set "TOOL_URL_MACOS_X64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-darwin-x64.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://nodejs.org/dist/v%TOOL_VERSION%/node-v%TOOL_VERSION%-darwin-arm64.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bin/npx"
set "TOOL_BINARY_WINDOWS=npx.cmd"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
