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
export TOOL_VERSION="1.25.7"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="12e6d6a191091ae27dc31f6efc630e3a3b8ba409baf3573d955b196fdf086005"
export TOOL_CHECKSUM_LINUX_ARM64="ba611a53534135a81067240eff9508cd7e256c560edd5d8c2fef54f083c07129"
export TOOL_CHECKSUM_WINDOWS_X64="c75e5f4ff62d085cc0017be3ad19d5536f46825fa05db06ec468941f847e3228"
export TOOL_CHECKSUM_WINDOWS_ARM64="807033f85931bc4a589ca8497535dcbeb1f30d506e47fa200f5f04c4a71c3d9f"
export TOOL_CHECKSUM_MACOS_X64="bf5050a2152f4053837b886e8d9640c829dbacbc3370f913351eb0904cb706f5"
export TOOL_CHECKSUM_MACOS_ARM64="ff18369ffad05c57d5bed888b660b31385f3c913670a83ef557cdfd98ea9ae1b"

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
set "TOOL_VERSION=1.25.7"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=12e6d6a191091ae27dc31f6efc630e3a3b8ba409baf3573d955b196fdf086005"
set "TOOL_CHECKSUM_LINUX_ARM64=ba611a53534135a81067240eff9508cd7e256c560edd5d8c2fef54f083c07129"
set "TOOL_CHECKSUM_WINDOWS_X64=c75e5f4ff62d085cc0017be3ad19d5536f46825fa05db06ec468941f847e3228"
set "TOOL_CHECKSUM_WINDOWS_ARM64=807033f85931bc4a589ca8497535dcbeb1f30d506e47fa200f5f04c4a71c3d9f"
set "TOOL_CHECKSUM_MACOS_X64=bf5050a2152f4053837b886e8d9640c829dbacbc3370f913351eb0904cb706f5"
set "TOOL_CHECKSUM_MACOS_ARM64=ff18369ffad05c57d5bed888b660b31385f3c913670a83ef557cdfd98ea9ae1b"

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