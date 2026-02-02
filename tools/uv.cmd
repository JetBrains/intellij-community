:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# uv wrapper - Unix section
# Downloads and executes uv with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/uv.cmd
# to verify all platform checksums before committing.

set -eu

# uv configuration
export TOOL_NAME="uv"
export TOOL_VERSION="0.9.24"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="fb13ad85106da6b21dd16613afca910994446fe94a78ee0b5bed9c75cd066078"
export TOOL_CHECKSUM_LINUX_ARM64="9b291a1a4f2fefc430e4fc49c00cb93eb448d41c5c79edf45211ceffedde3334"
export TOOL_CHECKSUM_WINDOWS_X64="cf9d6fa12017199d19c6f9a8f7f55811c8c04d70681b8cb6d89ffb179f08cf1f"
export TOOL_CHECKSUM_WINDOWS_ARM64="40ceb66af2667fc9b4d30a65ad8b8795d4effc39a44019b4218ad03f8f1d5a14"
export TOOL_CHECKSUM_MACOS_X64="fda9b3203cce6ec3a37177440c33c4c1963c4957fff17e2820c60ab6ccd625da"
export TOOL_CHECKSUM_MACOS_ARM64="89661d9a16682197086df54bb43d0b03e58e23d4d9360fc8c6c0166f2828fd71"

# Download URLs (direct GitHub releases)
export TOOL_URL_LINUX_X64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-x86_64-unknown-linux-gnu.tar.gz"
export TOOL_URL_LINUX_ARM64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-aarch64-unknown-linux-gnu.tar.gz"
export TOOL_URL_WINDOWS_X64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-x86_64-pc-windows-msvc.zip"
export TOOL_URL_WINDOWS_ARM64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-aarch64-pc-windows-msvc.zip"
export TOOL_URL_MACOS_X64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-x86_64-apple-darwin.tar.gz"
export TOOL_URL_MACOS_ARM64="https://github.com/astral-sh/uv/releases/download/${TOOL_VERSION}/uv-aarch64-apple-darwin.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="uv"
export TOOL_BINARY_WINDOWS="uv.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM uv wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\uv.cmd
REM to verify all platform checksums before committing.

REM uv configuration
set "TOOL_NAME=uv"
set "TOOL_VERSION=0.9.24"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=fb13ad85106da6b21dd16613afca910994446fe94a78ee0b5bed9c75cd066078"
set "TOOL_CHECKSUM_LINUX_ARM64=9b291a1a4f2fefc430e4fc49c00cb93eb448d41c5c79edf45211ceffedde3334"
set "TOOL_CHECKSUM_WINDOWS_X64=cf9d6fa12017199d19c6f9a8f7f55811c8c04d70681b8cb6d89ffb179f08cf1f"
set "TOOL_CHECKSUM_WINDOWS_ARM64=40ceb66af2667fc9b4d30a65ad8b8795d4effc39a44019b4218ad03f8f1d5a14"
set "TOOL_CHECKSUM_MACOS_X64=fda9b3203cce6ec3a37177440c33c4c1963c4957fff17e2820c60ab6ccd625da"
set "TOOL_CHECKSUM_MACOS_ARM64=89661d9a16682197086df54bb43d0b03e58e23d4d9360fc8c6c0166f2828fd71"

REM Download URLs (direct GitHub releases)
set "TOOL_URL_LINUX_X64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-x86_64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-aarch64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-x86_64-pc-windows-msvc.zip"
set "TOOL_URL_WINDOWS_ARM64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-aarch64-pc-windows-msvc.zip"
set "TOOL_URL_MACOS_X64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-x86_64-apple-darwin.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://github.com/astral-sh/uv/releases/download/%TOOL_VERSION%/uv-aarch64-apple-darwin.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=uv"
set "TOOL_BINARY_WINDOWS=uv.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
