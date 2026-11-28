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
export TOOL_VERSION="0.13.10"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="bf65ac1a0af10f92c522d91ac096c7c0a39657647b155577e7836c2cbbb4526c"
export TOOL_CHECKSUM_LINUX_ARM64="14590f644805a6234ffe071154340669e38137f38e60aa9629690119704107d2"
export TOOL_CHECKSUM_WINDOWS_X64="297b3881fe1adfb922becc2c1613202abb41dc83ea5ff43d472aa087ca1f8683"
export TOOL_CHECKSUM_WINDOWS_ARM64="f223bcd3fa010174ffeeb885cc1d5334bb81388640c65baa5b8b42b47017337e"
export TOOL_CHECKSUM_MACOS_X64="153e03197588204c04fc4c81cc4bafbfa4280222a385e4ee36a87aeec0ae63b4"
export TOOL_CHECKSUM_MACOS_ARM64="e419bf07533bdbf48795fc793601c834dd79db57a8c49ce8e25cfe3a8639958d"

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
set "TOOL_VERSION=0.13.10"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=bf65ac1a0af10f92c522d91ac096c7c0a39657647b155577e7836c2cbbb4526c"
set "TOOL_CHECKSUM_LINUX_ARM64=14590f644805a6234ffe071154340669e38137f38e60aa9629690119704107d2"
set "TOOL_CHECKSUM_WINDOWS_X64=297b3881fe1adfb922becc2c1613202abb41dc83ea5ff43d472aa087ca1f8683"
set "TOOL_CHECKSUM_WINDOWS_ARM64=f223bcd3fa010174ffeeb885cc1d5334bb81388640c65baa5b8b42b47017337e"
set "TOOL_CHECKSUM_MACOS_X64=153e03197588204c04fc4c81cc4bafbfa4280222a385e4ee36a87aeec0ae63b4"
set "TOOL_CHECKSUM_MACOS_ARM64=e419bf07533bdbf48795fc793601c834dd79db57a8c49ce8e25cfe3a8639958d"

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
