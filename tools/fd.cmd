:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# fd wrapper - Unix section
# Downloads and executes fd with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/fd.cmd
# to verify all platform checksums before committing.

set -eu

# fd configuration
export TOOL_NAME="fd"
export TOOL_VERSION="v10.5.2"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="7e2d07fa2b8807642aaec67b7e2972a9fbe4db26fcb340a7372392db42b26694"
export TOOL_CHECKSUM_LINUX_ARM64="4b144f38bd7352cd4c95020b58e8e575476e60edcdf1dc5e0733e3d01efba6dd"
export TOOL_CHECKSUM_WINDOWS_X64="7edac75ef96b7e9a01ebb251a21ee86ea365a09adcfb865f1ec3346a906153b6"
export TOOL_CHECKSUM_WINDOWS_ARM64="0ee570ad513e6e1909642dbd79bf7177cf3457652e912c27726582c0f49df86e"
export TOOL_CHECKSUM_MACOS_X64="0f02e6000a8c896cd254cf4dccc7da1e604e77a32c6783609eed5e4cb07785e3"
export TOOL_CHECKSUM_MACOS_ARM64="81bf98a16d3d7230e4a9123fcede243d71ea31a577113476ed43f1cdf4773162"

# Download URLs (GitHub releases through the cache redirector)
export TOOL_URL_LINUX_X64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-unknown-linux-musl.tar.gz"
export TOOL_URL_LINUX_ARM64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-unknown-linux-gnu.tar.gz"
export TOOL_URL_WINDOWS_X64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-pc-windows-msvc.zip"
export TOOL_URL_WINDOWS_ARM64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-pc-windows-msvc.zip"
export TOOL_URL_MACOS_X64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-apple-darwin.tar.gz"
export TOOL_URL_MACOS_ARM64="https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-apple-darwin.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="fd"
export TOOL_BINARY_WINDOWS="fd.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM fd wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\fd.cmd
REM to verify all platform checksums before committing.

REM fd configuration
set "TOOL_NAME=fd"
set "TOOL_VERSION=v10.5.2"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=7e2d07fa2b8807642aaec67b7e2972a9fbe4db26fcb340a7372392db42b26694"
set "TOOL_CHECKSUM_LINUX_ARM64=4b144f38bd7352cd4c95020b58e8e575476e60edcdf1dc5e0733e3d01efba6dd"
set "TOOL_CHECKSUM_WINDOWS_X64=7edac75ef96b7e9a01ebb251a21ee86ea365a09adcfb865f1ec3346a906153b6"
set "TOOL_CHECKSUM_WINDOWS_ARM64=0ee570ad513e6e1909642dbd79bf7177cf3457652e912c27726582c0f49df86e"
set "TOOL_CHECKSUM_MACOS_X64=0f02e6000a8c896cd254cf4dccc7da1e604e77a32c6783609eed5e4cb07785e3"
set "TOOL_CHECKSUM_MACOS_ARM64=81bf98a16d3d7230e4a9123fcede243d71ea31a577113476ed43f1cdf4773162"

REM Download URLs (GitHub releases through the cache redirector)
set "TOOL_URL_LINUX_X64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-unknown-linux-musl.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-pc-windows-msvc.zip"
set "TOOL_URL_WINDOWS_ARM64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-pc-windows-msvc.zip"
set "TOOL_URL_MACOS_X64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-apple-darwin.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://cache-redirector.jetbrains.com/github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-apple-darwin.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=fd"
set "TOOL_BINARY_WINDOWS=fd.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
