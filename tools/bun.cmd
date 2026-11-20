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
export TOOL_VERSION="1.4.5"

# SHA-256 checksums for each platform (Bun v1.4.5)
export TOOL_CHECKSUM_LINUX_X64="94559b3b528995a49452f9f2404cec74f2efd7d06e0a000a8cd214b02c3666eb"
export TOOL_CHECKSUM_LINUX_ARM64="18656a3bf0ea5641f5796ae4213b75255579a70a0fded2f337933785568cdf84"
export TOOL_CHECKSUM_WINDOWS_X64="815ab45ec7f549abf87e6869584b506eab088e24225683957b7e150b47d6de30"
export TOOL_CHECKSUM_WINDOWS_ARM64="41f57d49c3c6811888d9633c569e40b4eb1f7e92b5e5336e3abd3af03187d77a"
export TOOL_CHECKSUM_MACOS_X64="a4972e778cce4b6a194d7d289f326aa61576d71d80b7a53e9da2f3739566e5fd"
export TOOL_CHECKSUM_MACOS_ARM64="b5d6bc2e13caf14e57c89661e89efd073ff16165b41bfe43882616410609cd10"

# Download URLs: the GitHub release archives through the cache redirector. The bun_* http_archives in the ultimate
# MODULE.bazel download the same archives, because `bazel_downloader.cfg` routes github.com through the redirector.
TOOL_RELEASE="https://cache-redirector.jetbrains.com/github.com/oven-sh/bun/releases/download/bun-v${TOOL_VERSION}"
export TOOL_URL_LINUX_X64="${TOOL_RELEASE}/bun-linux-x64.zip"
export TOOL_URL_LINUX_ARM64="${TOOL_RELEASE}/bun-linux-aarch64.zip"
export TOOL_URL_WINDOWS_X64="${TOOL_RELEASE}/bun-windows-x64.zip"
export TOOL_URL_WINDOWS_ARM64="${TOOL_RELEASE}/bun-windows-aarch64.zip"
export TOOL_URL_MACOS_X64="${TOOL_RELEASE}/bun-darwin-x64.zip"
export TOOL_URL_MACOS_ARM64="${TOOL_RELEASE}/bun-darwin-aarch64.zip"

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
set "TOOL_VERSION=1.4.5"

REM SHA-256 checksums for each platform (Bun v1.4.5)
set "TOOL_CHECKSUM_LINUX_X64=94559b3b528995a49452f9f2404cec74f2efd7d06e0a000a8cd214b02c3666eb"
set "TOOL_CHECKSUM_LINUX_ARM64=18656a3bf0ea5641f5796ae4213b75255579a70a0fded2f337933785568cdf84"
set "TOOL_CHECKSUM_WINDOWS_X64=815ab45ec7f549abf87e6869584b506eab088e24225683957b7e150b47d6de30"
set "TOOL_CHECKSUM_WINDOWS_ARM64=41f57d49c3c6811888d9633c569e40b4eb1f7e92b5e5336e3abd3af03187d77a"
set "TOOL_CHECKSUM_MACOS_X64=a4972e778cce4b6a194d7d289f326aa61576d71d80b7a53e9da2f3739566e5fd"
set "TOOL_CHECKSUM_MACOS_ARM64=b5d6bc2e13caf14e57c89661e89efd073ff16165b41bfe43882616410609cd10"

REM Download URLs: the GitHub release archives through the cache redirector, as in the POSIX half.
set "TOOL_RELEASE=https://cache-redirector.jetbrains.com/github.com/oven-sh/bun/releases/download/bun-v%TOOL_VERSION%"
set "TOOL_URL_LINUX_X64=%TOOL_RELEASE%/bun-linux-x64.zip"
set "TOOL_URL_LINUX_ARM64=%TOOL_RELEASE%/bun-linux-aarch64.zip"
set "TOOL_URL_WINDOWS_X64=%TOOL_RELEASE%/bun-windows-x64.zip"
set "TOOL_URL_WINDOWS_ARM64=%TOOL_RELEASE%/bun-windows-aarch64.zip"
set "TOOL_URL_MACOS_X64=%TOOL_RELEASE%/bun-darwin-x64.zip"
set "TOOL_URL_MACOS_ARM64=%TOOL_RELEASE%/bun-darwin-aarch64.zip"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bun"
set "TOOL_BINARY_WINDOWS=bun.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
