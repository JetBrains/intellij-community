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
export TOOL_VERSION="v10.3.0"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="2b6bfaae8c48f12050813c2ffe1884c61ea26e750d803df9c9114550a314cd14"
export TOOL_CHECKSUM_LINUX_ARM64="66f297e404400a3358e9a0c0b2f3f4725956e7e4435427a9ae56e22adbe73a68"
export TOOL_CHECKSUM_WINDOWS_X64="318aa2a6fa664325933e81fda60d523fff29444129e91ebf0726b5b3bcd8b059"
export TOOL_CHECKSUM_WINDOWS_ARM64="bf9b1e31bcac71c1e95d49c56f0d872f525b95d03854e94b1d4dd6786f825cc5"
export TOOL_CHECKSUM_MACOS_X64="50d30f13fe3d5914b14c4fff5abcbd4d0cdab4b855970a6956f4f006c17117a3"
export TOOL_CHECKSUM_MACOS_ARM64="0570263812089120bc2a5d84f9e65cd0c25e4a4d724c80075c357239c74ae904"

# Download URLs (direct GitHub releases)
export TOOL_URL_LINUX_X64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-unknown-linux-musl.tar.gz"
export TOOL_URL_LINUX_ARM64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-unknown-linux-gnu.tar.gz"
export TOOL_URL_WINDOWS_X64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-pc-windows-msvc.zip"
export TOOL_URL_WINDOWS_ARM64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-pc-windows-msvc.zip"
export TOOL_URL_MACOS_X64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-x86_64-apple-darwin.tar.gz"
export TOOL_URL_MACOS_ARM64="https://github.com/sharkdp/fd/releases/download/${TOOL_VERSION}/fd-${TOOL_VERSION}-aarch64-apple-darwin.tar.gz"

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
set "TOOL_VERSION=v10.3.0"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=2b6bfaae8c48f12050813c2ffe1884c61ea26e750d803df9c9114550a314cd14"
set "TOOL_CHECKSUM_LINUX_ARM64=66f297e404400a3358e9a0c0b2f3f4725956e7e4435427a9ae56e22adbe73a68"
set "TOOL_CHECKSUM_WINDOWS_X64=318aa2a6fa664325933e81fda60d523fff29444129e91ebf0726b5b3bcd8b059"
set "TOOL_CHECKSUM_WINDOWS_ARM64=bf9b1e31bcac71c1e95d49c56f0d872f525b95d03854e94b1d4dd6786f825cc5"
set "TOOL_CHECKSUM_MACOS_X64=50d30f13fe3d5914b14c4fff5abcbd4d0cdab4b855970a6956f4f006c17117a3"
set "TOOL_CHECKSUM_MACOS_ARM64=0570263812089120bc2a5d84f9e65cd0c25e4a4d724c80075c357239c74ae904"

REM Download URLs (direct GitHub releases)
set "TOOL_URL_LINUX_X64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-unknown-linux-musl.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-pc-windows-msvc.zip"
set "TOOL_URL_WINDOWS_ARM64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-pc-windows-msvc.zip"
set "TOOL_URL_MACOS_X64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-x86_64-apple-darwin.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://github.com/sharkdp/fd/releases/download/%TOOL_VERSION%/fd-%TOOL_VERSION%-aarch64-apple-darwin.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=fd"
set "TOOL_BINARY_WINDOWS=fd.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
