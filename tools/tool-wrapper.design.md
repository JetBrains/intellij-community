# Tool Wrapper Infrastructure

A reusable system for downloading, verifying, and executing external tools with
fixed versions and cryptographic checksums.

## Quick Start

```bash
# Run uv (downloads automatically on first run)
./community/tools/uv.cmd --version

# Run npx (downloads Node.js automatically on first run)
./community/tools/npx.cmd --version

# Verify all platform binaries (CI/release verification)
TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/uv.cmd
TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/npx.cmd
```

## Design Overview

### Architecture

```
┌─────────────┐     sets env vars      ┌──────────────────┐
│   uv.cmd    │ ────────────────────▶  │  tool-wrapper.*  │
│  (polyglot) │                        │  (.sh / .cmd)    │
└─────────────┘                        └────────┬─────────┘
                                                │
                    ┌───────────────────────────┼───────────────────────────┐
                    │                           │                           │
                    ▼                           ▼                           ▼
            ┌───────────────┐          ┌───────────────┐          ┌───────────────┐
            │ Check cache   │          │ Download to   │          │ Verify SHA256 │
            │ for binary    │──miss──▶ │ temp file     │────────▶ │ checksum      │
            └───────────────┘          └───────────────┘          └───────┬───────┘
                    │                                                     │
                    │ hit                                         ┌───────┴───────┐
                    │                                             │               │
                    ▼                                       pass  ▼         fail  ▼
            ┌───────────────┐          ┌───────────────┐  ┌───────────────┐ ┌─────────┐
            │ Execute with  │◀─────────│ Extract with  │◀─│ Validate      │ │ Delete  │
            │ passthrough   │          │ strip & cache │  │ structure     │ │ & exit  │
            │ arguments     │          └───────────────┘  └───────────────┘ └─────────┘
            └───────────────┘
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Configuration storage** | Embedded in script | Self-contained, single file to update |
| **Checksum algorithm** | SHA-256 | Industry standard, published by releases |
| **Error handling** | Hard fail, never save bad downloads | Security first |
| **Download tool (Unix)** | curl | Standard, available everywhere |
| **Download tool (Windows)** | PowerShell (only when needed) | Native, no external dependencies |
| **Archive extraction** | Auto-detect and strip if nested | Handles both nested (single top-level dir) and flat archives |
| **Archive structure** | Support nested and flat | Nested archives stripped, flat archives extracted directly |
| **Retry logic** | None | Simple, user can retry manually |
| **Version override** | Not supported | Version pinned in script, no bypass |
| **Windows arch detection** | Registry query | More reliable than env var |
| **Path handling** | Quote all paths | Support spaces in usernames/paths |
| **Concurrency** | File locking (hard links on Unix, Mutex on Windows) | Safe for parallel runs |
| **Cache scope** | Full extracted tree | Supports tools with multiple files |

### Supported Platforms

| Platform | Architecture | Target Triple (uv) |
|----------|--------------|-------------------|
| Linux | x86_64 | x86_64-unknown-linux-gnu |
| Linux | aarch64 | aarch64-unknown-linux-gnu |
| Windows | x64 | x86_64-pc-windows-msvc |
| Windows | arm64 | aarch64-pc-windows-msvc |
| macOS | x64 | x86_64-apple-darwin |
| macOS | arm64 | aarch64-apple-darwin |

### Cache Structure

```
# Linux
~/.cache/JetBrains/monorepo-tools/<tool>/<version>/<extracted-contents>

# macOS
~/Library/Caches/JetBrains/monorepo-tools/<tool>/<version>/<extracted-contents>

# Windows
%LOCALAPPDATA%\JetBrains\monorepo-tools\<tool>\<version>\<extracted-contents>
```

## Adding a New Tool

1. Create `community/tools/<toolname>.cmd` (polyglot script)
2. Set required environment variables:
   - `TOOL_NAME` - tool name (used in cache path)
   - `TOOL_VERSION` - pinned version
   - `TOOL_CHECKSUM_<PLATFORM>` - SHA-256 for each platform
   - `TOOL_URL_<PLATFORM>` - download URL for each platform
   - `TOOL_BINARY_UNIX` - path to binary for Linux/macOS (e.g., `uv` or `bin/npx`)
   - `TOOL_BINARY_WINDOWS` - path to binary for Windows (e.g., `uv.exe` or `npx.cmd`)
3. Call `tool-wrapper.sh` (Unix) or `tool-wrapper.cmd` (Windows)

### Environment Variable Reference

```bash
# Required
TOOL_NAME="uv"
TOOL_VERSION="0.9.24"

# Checksums (SHA-256 of archive, not binary)
TOOL_CHECKSUM_LINUX_X64="abc123..."
TOOL_CHECKSUM_LINUX_ARM64="def456..."
TOOL_CHECKSUM_WINDOWS_X64="ghi789..."
TOOL_CHECKSUM_WINDOWS_ARM64="jkl012..."
TOOL_CHECKSUM_MACOS_ARM64="mno345..."

# Download URLs
TOOL_URL_LINUX_X64="https://github.com/.../uv-x86_64-unknown-linux-gnu.tar.gz"
TOOL_URL_LINUX_ARM64="https://github.com/.../uv-aarch64-unknown-linux-gnu.tar.gz"
TOOL_URL_WINDOWS_X64="https://github.com/.../uv-x86_64-pc-windows-msvc.zip"
TOOL_URL_WINDOWS_ARM64="https://github.com/.../uv-aarch64-pc-windows-msvc.zip"
TOOL_URL_MACOS_ARM64="https://github.com/.../uv-aarch64-apple-darwin.tar.gz"

# Binary path within extracted archive (after top-level directory is stripped)
TOOL_BINARY_UNIX="uv"         # Used for Linux and macOS
TOOL_BINARY_WINDOWS="uv.exe"  # Used for Windows

# Example for tools with subdirectory paths:
TOOL_BINARY_UNIX="bin/npx"    # Node.js on Unix
TOOL_BINARY_WINDOWS="npx.cmd" # Node.js on Windows
```

## Archive Structure Requirements

The wrapper supports two archive structures:

### Nested Archives (Preferred)

Archives with exactly one top-level directory have it stripped during extraction
(like `tar --strip-components=1`), so `TOOL_BINARY` should be the path relative
to the contents of that top-level directory.

**Example:** If an archive contains:
```
uv-0.9.24-x86_64-unknown-linux-gnu/
├── uv
└── README.md
```

Then `TOOL_BINARY="uv"` and after extraction the cache will contain:
```
~/.cache/JetBrains/monorepo-tools/uv/0.9.24/
├── uv
└── README.md
```

### Flat Archives

Archives with multiple top-level entries (or files at root) are extracted directly
without stripping. `TOOL_BINARY` should be the direct path to the binary.

**Example:** If an archive contains:
```
uv.exe
uvw.exe
uvx.exe
```

Then `TOOL_BINARY="uv.exe"` (on Windows) and the cache will contain:
```
%LOCALAPPDATA%\JetBrains\monorepo-tools\uv\0.9.24\
├── uv.exe
├── uvw.exe
└── uvx.exe
```

Note: Some tools (like uv) use nested structure for tar.gz but flat structure for
zip archives. The wrapper auto-detects and handles both cases.

## Verification Mode

Set `TOOL_VERIFY_ALL_PLATFORMS=1` to download and verify all 5 platform variants:

```bash
TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/uv.cmd
```

This mode:
- Downloads all platform archives to a temp directory
- Computes SHA-256 checksum for each
- Reports archive structure type (nested or flat)
- Compares against expected values
- Extracts archive and verifies `TOOL_BINARY` exists (using `TOOL_BINARY_UNIX`/`TOOL_BINARY_WINDOWS` if set)
- Prints detailed report (URL, expected checksum, actual checksum, file size, structure, binary)
- Cleans up temp directory
- Exits 0 if all pass, non-zero if any fail

Useful for:
- CI verification before release
- Validating checksums after version bump
- Ensuring archives haven't been tampered with
- Verifying archive structure is compatible
- Verifying binary paths are correct for all platforms

## Concurrency Safety

The tool wrapper is safe for parallel execution (e.g., multiple CI jobs or build processes).

**Mechanism:**
- Unix: Hard link-based locking (atomic lock acquisition)
- Windows: PowerShell global Mutex

**Behavior:**
- First process acquires lock and downloads
- Other processes wait and display "Waiting for process N to finish downloading..."
- When lock is released, waiting processes detect completion via `.complete` flag file
- All processes use the cached binary

```bash
# Safe to run in parallel
for i in {1..10}; do ./community/tools/uv.cmd --version & done; wait
```

## Security

### Threat Model

- **Supply chain attacks**: Mitigated by pinned versions and checksums
- **MITM attacks**: Mitigated by HTTPS + checksum verification
- **Corrupted downloads**: Mitigated by verifying before moving to cache
- **Unexpected archives**: Mitigated by validating single top-level directory

### Checksum Verification

1. Download to temp file (never directly to cache)
2. Compute SHA-256 of downloaded file
3. Compare against embedded expected checksum
4. Validate archive has exactly one top-level directory
5. Only extract and cache if all checks pass
6. Validate `TOOL_BINARY` exists after extraction
7. Delete temp file on any failure, exit with error

### Updating Tool Versions

When updating a tool version:
1. Update `TOOL_VERSION` and all `TOOL_CHECKSUM_*` values in the script
2. **MANDATORY**: Run `TOOL_VERIFY_ALL_PLATFORMS=1 ./community/tools/<tool>.cmd` to verify all checksums and archive structure
3. Verify the tool works: `./community/tools/<tool>.cmd --version`
4. Commit changes

**IMPORTANT**: Each wrapper script contains a reminder comment about this requirement.
Never skip the verification step - checksums from release notes may differ from actual archives.
