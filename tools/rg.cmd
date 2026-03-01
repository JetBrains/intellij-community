:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# rg (ripgrep) wrapper - Unix section
# Downloads and executes ripgrep with version pinning and checksum verification
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/rg.cmd
# to verify all platform checksums before committing.

set -eu

# ripgrep configuration
export TOOL_NAME="rg"
export TOOL_VERSION="15.1.0"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="1c9297be4a084eea7ecaedf93eb03d058d6faae29bbc57ecdaf5063921491599"
export TOOL_CHECKSUM_LINUX_ARM64="2b661c6ef508e902f388e9098d9c4c5aca72c87b55922d94abdba830b4dc885e"
export TOOL_CHECKSUM_WINDOWS_X64="124510b94b6baa3380d051fdf4650eaa80a302c876d611e9dba0b2e18d87493a"
export TOOL_CHECKSUM_WINDOWS_ARM64="00d931fb5237c9696ca49308818edb76d8eb6fc132761cb2a1bd616b2df02f8e"
export TOOL_CHECKSUM_MACOS_X64="64811cb24e77cac3057d6c40b63ac9becf9082eedd54ca411b475b755d334882"
export TOOL_CHECKSUM_MACOS_ARM64="378e973289176ca0c6054054ee7f631a065874a352bf43f0fa60ef079b6ba715"

# Download URLs (direct GitHub releases)
export TOOL_URL_LINUX_X64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-unknown-linux-musl.tar.gz"
export TOOL_URL_LINUX_ARM64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-unknown-linux-gnu.tar.gz"
export TOOL_URL_WINDOWS_X64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-pc-windows-msvc.zip"
export TOOL_URL_WINDOWS_ARM64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-pc-windows-msvc.zip"
export TOOL_URL_MACOS_X64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-x86_64-apple-darwin.tar.gz"
export TOOL_URL_MACOS_ARM64="https://github.com/BurntSushi/ripgrep/releases/download/${TOOL_VERSION}/ripgrep-${TOOL_VERSION}-aarch64-apple-darwin.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="rg"
export TOOL_BINARY_WINDOWS="rg.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM rg (ripgrep) wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\rg.cmd
REM to verify all platform checksums before committing.

REM ripgrep configuration
set "TOOL_NAME=rg"
set "TOOL_VERSION=15.1.0"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=1c9297be4a084eea7ecaedf93eb03d058d6faae29bbc57ecdaf5063921491599"
set "TOOL_CHECKSUM_LINUX_ARM64=2b661c6ef508e902f388e9098d9c4c5aca72c87b55922d94abdba830b4dc885e"
set "TOOL_CHECKSUM_WINDOWS_X64=124510b94b6baa3380d051fdf4650eaa80a302c876d611e9dba0b2e18d87493a"
set "TOOL_CHECKSUM_WINDOWS_ARM64=00d931fb5237c9696ca49308818edb76d8eb6fc132761cb2a1bd616b2df02f8e"
set "TOOL_CHECKSUM_MACOS_X64=64811cb24e77cac3057d6c40b63ac9becf9082eedd54ca411b475b755d334882"
set "TOOL_CHECKSUM_MACOS_ARM64=378e973289176ca0c6054054ee7f631a065874a352bf43f0fa60ef079b6ba715"

REM Download URLs (direct GitHub releases)
set "TOOL_URL_LINUX_X64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-unknown-linux-musl.tar.gz"
set "TOOL_URL_LINUX_ARM64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-unknown-linux-gnu.tar.gz"
set "TOOL_URL_WINDOWS_X64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-pc-windows-msvc.zip"
set "TOOL_URL_WINDOWS_ARM64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-pc-windows-msvc.zip"
set "TOOL_URL_MACOS_X64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-x86_64-apple-darwin.tar.gz"
set "TOOL_URL_MACOS_ARM64=https://github.com/BurntSushi/ripgrep/releases/download/%TOOL_VERSION%/ripgrep-%TOOL_VERSION%-aarch64-apple-darwin.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=rg"
set "TOOL_BINARY_WINDOWS=rg.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
