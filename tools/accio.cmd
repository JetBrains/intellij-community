:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# IMPORTANT: Read community/tools/tool-wrapper.design.md before making ANY modifications to this file.

# accio wrapper - Unix section
# Downloads and executes accio with version pinning and checksum verification.
# The archives are the release archives of a JetBrains/accio commit (release.yml), mirrored to intellij-build-dependencies.
# An accio on the PATH wins over the pin: a developer build stays the one in use. ACCIO_WRAPPER_ACTIVE stops a loop
# when that accio is this wrapper.
#
# To bump the pin:
#   1. gh workflow run release.yml -R JetBrains/accio --ref <branch>, then gh run download <run-id> when it is green.
#   2. Upload the six archives to accio/<version>/ in https://jetbrains.team/p/ij/packages/files/intellij-build-dependencies.
#      <version> is the Cargo.toml version and the short commit sha.
#   3. Change TOOL_VERSION, ACCIO_ARCHIVE_VERSION and the checksums in both sections. The .sha256 files hold them.
#
# IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
#   TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/accio.cmd
# to verify all platform checksums before committing.

set -eu

if [ -z "${ACCIO_WRAPPER_ACTIVE:-}" ] && global="$(command -v accio 2>/dev/null)"; then
  export ACCIO_WRAPPER_ACTIVE=1
  exec "$global" "$@"
fi

# accio configuration
export TOOL_NAME="accio"
export TOOL_VERSION="0.9.0-fec2b92"
ACCIO_ARCHIVE_VERSION="v0.9.0"

# SHA-256 checksums for each platform
export TOOL_CHECKSUM_LINUX_X64="b015851e6edaa6429135ecfa7c48936474e5755fbe781524c988fdbf27d7bad8"
export TOOL_CHECKSUM_LINUX_ARM64="8dbd30b3ac0b4916451dbcb4e007d44223657746a29c4ef836a69a8fe0149714"
export TOOL_CHECKSUM_WINDOWS_X64="d5e1b026d613ba99ae0eb21160c9f8939cd296b4b782e9b3fd38fa251c984ca6"
export TOOL_CHECKSUM_WINDOWS_ARM64="6a63874c81a9e014c810e8a71d838c9135b1ec74c9a4c77541985aab0bb797e9"
export TOOL_CHECKSUM_MACOS_X64="3ade2c5b7b897581aae4bc42a2e2163c3534836bddfbe363453764978fbe03ec"
export TOOL_CHECKSUM_MACOS_ARM64="263517c6a3fb7c44ed1958323d9aff49b8126a8979eb0ffbb83e5e363fe43dff"

# Download URLs (intellij-build-dependencies through the cache redirector)
base="https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/accio/${TOOL_VERSION}/accio-${ACCIO_ARCHIVE_VERSION}"
export TOOL_URL_LINUX_X64="${base}-linux-x86_64.tar.gz"
export TOOL_URL_LINUX_ARM64="${base}-linux-aarch64.tar.gz"
export TOOL_URL_WINDOWS_X64="${base}-windows-x86_64.zip"
export TOOL_URL_WINDOWS_ARM64="${base}-windows-aarch64.zip"
export TOOL_URL_MACOS_X64="${base}-macos-x86_64.tar.gz"
export TOOL_URL_MACOS_ARM64="${base}-macos-aarch64.tar.gz"

# Binary path within extracted archive
export TOOL_BINARY_UNIX="accio"
export TOOL_BINARY_WINDOWS="accio.exe"

# Invoke wrapper
root="$(cd "$(dirname "$0")"; pwd)"
exec "$root/tool-wrapper.sh" "$@"

:CMDSCRIPT

setlocal

REM IMPORTANT: Read community\tools\tool-wrapper.design.md before making ANY modifications to this file.

REM accio wrapper - Windows section
REM IMPORTANT: After updating TOOL_VERSION or checksums, you MUST run:
REM   set TOOL_VERIFY_ALL_PLATFORMS=1 && community\tools\accio.cmd
REM to verify all platform checksums before committing.

REM An accio.exe on the PATH wins over the pin
if defined ACCIO_WRAPPER_ACTIVE goto :ACCIO_PINNED
set "ACCIO_WRAPPER_ACTIVE=1"
where /q accio.exe
if errorlevel 1 goto :ACCIO_PINNED
accio.exe %*
exit /B %ERRORLEVEL%
:ACCIO_PINNED

REM accio configuration
set "TOOL_NAME=accio"
set "TOOL_VERSION=0.9.0-fec2b92"
set "ACCIO_ARCHIVE_VERSION=v0.9.0"

REM SHA-256 checksums for each platform
set "TOOL_CHECKSUM_LINUX_X64=b015851e6edaa6429135ecfa7c48936474e5755fbe781524c988fdbf27d7bad8"
set "TOOL_CHECKSUM_LINUX_ARM64=8dbd30b3ac0b4916451dbcb4e007d44223657746a29c4ef836a69a8fe0149714"
set "TOOL_CHECKSUM_WINDOWS_X64=d5e1b026d613ba99ae0eb21160c9f8939cd296b4b782e9b3fd38fa251c984ca6"
set "TOOL_CHECKSUM_WINDOWS_ARM64=6a63874c81a9e014c810e8a71d838c9135b1ec74c9a4c77541985aab0bb797e9"
set "TOOL_CHECKSUM_MACOS_X64=3ade2c5b7b897581aae4bc42a2e2163c3534836bddfbe363453764978fbe03ec"
set "TOOL_CHECKSUM_MACOS_ARM64=263517c6a3fb7c44ed1958323d9aff49b8126a8979eb0ffbb83e5e363fe43dff"

REM Download URLs (intellij-build-dependencies through the cache redirector)
set "ACCIO_BASE_URL=https://cache-redirector.jetbrains.com/files/p/ij/intellij-build-dependencies/accio/%TOOL_VERSION%/accio-%ACCIO_ARCHIVE_VERSION%"
set "TOOL_URL_LINUX_X64=%ACCIO_BASE_URL%-linux-x86_64.tar.gz"
set "TOOL_URL_LINUX_ARM64=%ACCIO_BASE_URL%-linux-aarch64.tar.gz"
set "TOOL_URL_WINDOWS_X64=%ACCIO_BASE_URL%-windows-x86_64.zip"
set "TOOL_URL_WINDOWS_ARM64=%ACCIO_BASE_URL%-windows-aarch64.zip"
set "TOOL_URL_MACOS_X64=%ACCIO_BASE_URL%-macos-x86_64.tar.gz"
set "TOOL_URL_MACOS_ARM64=%ACCIO_BASE_URL%-macos-aarch64.tar.gz"

REM Binary path within extracted archive
set "TOOL_BINARY_UNIX=accio"
set "TOOL_BINARY_WINDOWS=accio.exe"

REM Invoke wrapper
call "%~dp0tool-wrapper.cmd" %*
exit /B %ERRORLEVEL%
