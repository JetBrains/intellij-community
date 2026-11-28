:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# Go wrapper - Unix section
# Downloads and executes Go with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/go.cmd
# to verify all platform checksums before committing.

set -eu

# Go configuration
export TOOL_NAME="go"
export TOOL_VERSION="1.27.4"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="7226736c2339719cda28fbf3a54d71aa63bcefe376e98cef00034268dab24840"
export TOOL_CHECKSUM_LINUX_ARM64="3392582988a273a87ab5043ee1b2c9c17a2c3c00580bf9326c47a2e0aaaeac92"
export TOOL_CHECKSUM_WINDOWS_X64="178ab98950ac69ab25069307dc6ade79f3816e414cce95b4da166a115cde1e7e"
export TOOL_CHECKSUM_WINDOWS_ARM64="9e7a72565792b8ab370b37ac9ac109799237abacb39ff2f541c438b963c0cc64"
export TOOL_CHECKSUM_MACOS_X64="2b799377704ef12db39bd254995d5c348c9cc8d86562ca817724390e56c38f43"
export TOOL_CHECKSUM_MACOS_ARM64="91a44a2b8a716600ad94a4f143e156116620db8828086a6e3602291be9bd0bb4"

# Download URLs (official Go downloads)
export TOOL_URL_LINUX_X64="https://go.dev/dl/go${TOOL_VERSION}.linux-amd64.tar.gz"
export TOOL_URL_LINUX_ARM64="https://go.dev/dl/go${TOOL_VERSION}.linux-arm64.tar.gz"
export TOOL_URL_WINDOWS_X64="https://go.dev/dl/go${TOOL_VERSION}.windows-amd64.zip"
export TOOL_URL_WINDOWS_ARM64="https://go.dev/dl/go${TOOL_VERSION}.windows-arm64.zip"
export TOOL_URL_MACOS_X64="https://go.dev/dl/go${TOOL_VERSION}.darwin-amd64.tar.gz"
export TOOL_URL_MACOS_ARM64="https://go.dev/dl/go${TOOL_VERSION}.darwin-arm64.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="bin/go"
export TOOL_BINARY_WINDOWS="bin/go.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM Go wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\go.cmd
REM to verify all platform checksums before committing.

REM Go configuration
set "TOOL_NAME=go"
set "TOOL_VERSION=1.27.4"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=7226736c2339719cda28fbf3a54d71aa63bcefe376e98cef00034268dab24840"
set "TOOL_CHECKSUM_LINUX_ARM64=3392582988a273a87ab5043ee1b2c9c17a2c3c00580bf9326c47a2e0aaaeac92"
set "TOOL_CHECKSUM_WINDOWS_X64=178ab98950ac69ab25069307dc6ade79f3816e414cce95b4da166a115cde1e7e"
set "TOOL_CHECKSUM_WINDOWS_ARM64=9e7a72565792b8ab370b37ac9ac109799237abacb39ff2f541c438b963c0cc64"
set "TOOL_CHECKSUM_MACOS_X64=2b799377704ef12db39bd254995d5c348c9cc8d86562ca817724390e56c38f43"
set "TOOL_CHECKSUM_MACOS_ARM64=91a44a2b8a716600ad94a4f143e156116620db8828086a6e3602291be9bd0bb4"

REM Download URLs (official Go downloads)
set "TOOL_URL_LINUX_X64=https://go.dev/dl/go%TOOL_VERSION%.linux-amd64.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://go.dev/dl/go%TOOL_VERSION%.linux-arm64.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://go.dev/dl/go%TOOL_VERSION%.windows-amd64.zip"
set "TOOL_URL_WINDOWS_ARM64=https://go.dev/dl/go%TOOL_VERSION%.windows-arm64.zip"
set "TOOL_URL_MACOS_X64=https://go.dev/dl/go%TOOL_VERSION%.darwin-amd64.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://go.dev/dl/go%TOOL_VERSION%.darwin-arm64.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=bin\go"
set "TOOL_BINARY_WINDOWS=bin/go.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%