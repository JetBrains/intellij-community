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
export TOOL_VERSION="1.4.2"

# SHA-256 checksums for each platform (Bun v1.4.2)
export TOOL_CHECKSUM_LINUX_X64="36368faef7527875d5ffa52e53cd48021741f2a83eb6208a8dd64068d422a913"
export TOOL_CHECKSUM_LINUX_ARM64="54328bbc2d9c8e0c9f892c544d66c57a83b84139e34909e5ee81758f1ac8fda7"
export TOOL_CHECKSUM_WINDOWS_X64="ce4c17497b2f29712a99d3d53f028de28cd42e3bacb8589599e7f000e49b6405"
export TOOL_CHECKSUM_WINDOWS_ARM64="a7a16b876a305fd1029c66dbd27007b4f6112ae896532f675878731a21e50cfd"
export TOOL_CHECKSUM_MACOS_X64="80520d7e17526308c9185d261679ac6d27798d3803a0e9f7ff9121ab8affb012"
export TOOL_CHECKSUM_MACOS_ARM64="90987a3a16d7db556d886ac3d551e7b6d3edf0a1cf43acaed622e8676be1d12f"

# Download URLs: the JetBrains mirror of the GitHub release archives, byte-identical. The same
# mirror serves the bun_* http_archives in the ultimate MODULE.bazel, so both pins share one source.
TOOL_MIRROR="https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/bun/${TOOL_VERSION}"
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
set "TOOL_VERSION=1.4.2"

REM SHA-256 checksums for each platform (Bun v1.4.2)
set "TOOL_CHECKSUM_LINUX_X64=36368faef7527875d5ffa52e53cd48021741f2a83eb6208a8dd64068d422a913"
set "TOOL_CHECKSUM_LINUX_ARM64=54328bbc2d9c8e0c9f892c544d66c57a83b84139e34909e5ee81758f1ac8fda7"
set "TOOL_CHECKSUM_WINDOWS_X64=ce4c17497b2f29712a99d3d53f028de28cd42e3bacb8589599e7f000e49b6405"
set "TOOL_CHECKSUM_WINDOWS_ARM64=a7a16b876a305fd1029c66dbd27007b4f6112ae896532f675878731a21e50cfd"
set "TOOL_CHECKSUM_MACOS_X64=80520d7e17526308c9185d261679ac6d27798d3803a0e9f7ff9121ab8affb012"
set "TOOL_CHECKSUM_MACOS_ARM64=90987a3a16d7db556d886ac3d551e7b6d3edf0a1cf43acaed622e8676be1d12f"

REM Download URLs: the JetBrains mirror of the GitHub release archives, byte-identical. The same
REM mirror serves the bun_* http_archives in the ultimate MODULE.bazel, so both pins share one source.
set "TOOL_MIRROR=https://packages.jetbrains.team/files/p/ij/intellij-build-dependencies/bun/%TOOL_VERSION%"
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
