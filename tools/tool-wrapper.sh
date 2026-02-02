#!/usr/bin/env bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Tool Wrapper - Reusable download/verify/execute wrapper for external tools
# See tool-wrapper.design.md for usage and environment variable reference

set -eu

# Required environment variables (set by calling script):
#   TOOL_NAME              - Tool name (e.g., "uv")
#   TOOL_VERSION           - Tool version (e.g., "0.9.24")
#   TOOL_CHECKSUM_<PLATFORM> - SHA-256 checksum for each platform
#   TOOL_URL_<PLATFORM>    - Download URL for each platform
#   TOOL_BINARY            - Path to binary within extracted archive (e.g., "uv" or "bin/npx")
#
# Platforms: LINUX_X64, LINUX_ARM64, WINDOWS_X64, WINDOWS_ARM64, MACOS_X64, MACOS_ARM64

die() {
  echo "ERROR: $*" >&2
  exit 1
}

info() {
  echo "$*" >&2
}

# Detect platform
detect_platform() {
  local os arch
  os=$(uname -s)
  arch=$(uname -m)

  case "$os" in
    Linux)
      case "$arch" in
        x86_64) echo "LINUX_X64" ;;
        aarch64|arm64) echo "LINUX_ARM64" ;;
        *) die "Unsupported Linux architecture: $arch" ;;
      esac
      ;;
    Darwin)
      case "$arch" in
        x86_64) echo "MACOS_X64" ;;
        arm64) echo "MACOS_ARM64" ;;
        *) die "Unsupported macOS architecture: $arch" ;;
      esac
      ;;
    *)
      die "Unsupported OS: $os"
      ;;
  esac
}

# Get cache directory for current platform
get_cache_dir() {
  case "$(uname -s)" in
    Linux)
      echo "${HOME}/.cache/JetBrains/monorepo-tools"
      ;;
    Darwin)
      echo "${HOME}/Library/Caches/JetBrains/monorepo-tools"
      ;;
    *)
      die "Unsupported OS for cache directory"
      ;;
  esac
}

# Get platform-specific checksum
get_checksum() {
  local platform="$1"
  local var_name="TOOL_CHECKSUM_${platform}"
  eval "echo \${${var_name}:-}"
}

# Get platform-specific URL
get_url() {
  local platform="$1"
  local var_name="TOOL_URL_${platform}"
  eval "echo \${${var_name}:-}"
}

# Get binary path for platform
get_binary_for_platform() {
  local platform="$1"
  case "$platform" in
    WINDOWS_*) echo "$TOOL_BINARY_WINDOWS" ;;
    *)         echo "$TOOL_BINARY_UNIX" ;;
  esac
}

# Compute SHA-256 checksum of a file
compute_checksum() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | cut -d' ' -f1
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$file" | cut -d' ' -f1
  else
    die "No SHA-256 tool found (need sha256sum or shasum)"
  fi
}

# Download a file
download_file() {
  local url="$1"
  local dest="$2"
  info "Downloading $url"
  if ! curl -fsSL -o "$dest" "$url"; then
    rm -f "$dest"
    die "Failed to download $url"
  fi
}

# Acquire lock for concurrent access protection
# Uses hard link for atomic lock acquisition (like java.cmd)
# Returns 0 if lock acquired, 1 if another process completed the download
acquire_lock() {
  local lock_dir="$1"
  local flag_file="$2"
  local lock_file="$lock_dir/.tool-wrapper-lock.pid"
  local tmp_lock_file="$lock_dir/.tmp.$$.pid"

  mkdir -p "$lock_dir"
  echo $$ > "$tmp_lock_file"

  while ! ln "$tmp_lock_file" "$lock_file" 2>/dev/null; do
    local lock_owner
    lock_owner=$(cat "$lock_file" 2>/dev/null || true)

    # Wait if lock owner is still running
    while [ -n "$lock_owner" ] && ps -p "$lock_owner" >/dev/null 2>&1; do
      info "Waiting for process $lock_owner to finish downloading $TOOL_NAME..."
      sleep 1
      lock_owner=$(cat "$lock_file" 2>/dev/null || true)

      # Check if another process completed the download
      if [ -f "$flag_file" ]; then
        rm -f "$tmp_lock_file"
        return 1  # Signal: already downloaded by another process
      fi
    done

    # Stale lock - remove it
    if [ -n "$lock_owner" ]; then
      info "Removing stale lock from process $lock_owner"
      rm -f "$lock_file"
    fi
  done

  rm -f "$tmp_lock_file"
  # Store lock file path in global variable for cleanup
  ACQUIRED_LOCK_FILE="$lock_file"
  return 0
}

release_lock() {
  if [ -n "${ACQUIRED_LOCK_FILE:-}" ]; then
    rm -f "$ACQUIRED_LOCK_FILE"
    ACQUIRED_LOCK_FILE=""
  fi
}

# Get archive structure type: "nested:<dir_name>" if single top-level directory, "flat" if multiple entries
get_archive_structure() {
  local archive="$1"
  local url="$2"

  local top_level_entries
  case "$url" in
    *.tar.gz|*.tgz)
      top_level_entries=$(tar -tzf "$archive" | cut -d'/' -f1 | sort -u | grep -v '^$')
      ;;
    *.zip)
      top_level_entries=$(unzip -Z1 "$archive" | cut -d'/' -f1 | sort -u | grep -v '^$')
      ;;
    *)
      die "Unknown archive format from URL: $url"
      ;;
  esac

  local entry_count
  entry_count=$(echo "$top_level_entries" | wc -l | tr -d ' ')

  if [ "$entry_count" -eq 1 ]; then
    echo "nested:$top_level_entries"
  else
    echo "flat"
  fi
}

# Extract archive: strips top-level directory if nested, extracts directly if flat
extract_archive() {
  local archive="$1"
  local target_dir="$2"
  local url="$3"
  local structure="$4"  # "nested:<dir_name>" or "flat"

  mkdir -p "$target_dir"

  if [ "$structure" = "flat" ]; then
    # Flat archive: extract directly without stripping
    case "$url" in
      *.tar.gz|*.tgz)
        tar -xzf "$archive" -C "$target_dir"
        ;;
      *.zip)
        unzip -q "$archive" -d "$target_dir"
        ;;
      *)
        die "Unknown archive format from URL: $url"
        ;;
    esac
  else
    # Nested archive: extract with top-level directory stripped
    case "$url" in
      *.tar.gz|*.tgz)
        tar --strip-components=1 -xzf "$archive" -C "$target_dir"
        ;;
      *.zip)
        # Zip has no --strip-components, extract to temp then move contents
        local temp_extract
        temp_extract=$(mktemp -d)
        unzip -q "$archive" -d "$temp_extract"

        # Find the single top-level directory and move its contents
        local top_dir
        top_dir=$(find "$temp_extract" -mindepth 1 -maxdepth 1 -type d | head -1)
        if [ -z "$top_dir" ]; then
          rm -rf "$temp_extract"
          die "No directory found in extracted archive"
        fi

        # Move contents up (strip top-level)
        mv "$top_dir"/* "$target_dir"/ 2>/dev/null || true
        mv "$top_dir"/.* "$target_dir"/ 2>/dev/null || true
        rm -rf "$temp_extract"
        ;;
      *)
        die "Unknown archive format from URL: $url"
        ;;
    esac
  fi
}

# Download, verify, extract, and cache a single platform
download_and_cache() {
  local platform="$1"
  local cache_dir="$2"
  local expected_checksum
  local download_url
  local temp_dir
  local temp_archive
  local actual_checksum
  local target_dir
  local flag_file

  expected_checksum=$(get_checksum "$platform")
  download_url=$(get_url "$platform")

  if [ -z "$expected_checksum" ]; then
    die "No checksum defined for platform: $platform"
  fi
  if [ -z "$download_url" ]; then
    die "No URL defined for platform: $platform"
  fi

  target_dir="$cache_dir/$TOOL_NAME/$TOOL_VERSION"
  flag_file="$target_dir/.complete"

  # Try to acquire lock
  if ! acquire_lock "$cache_dir/$TOOL_NAME" "$flag_file"; then
    # Another process completed the download
    info "Download completed by another process"
    return 0
  fi

  # Set up cleanup trap
  trap 'release_lock; rm -rf "${temp_dir:-}"' EXIT

  # Double-check after acquiring lock (another process may have completed)
  if [ -f "$flag_file" ] && grep -qx "$expected_checksum" "$flag_file" 2>/dev/null; then
    info "Already downloaded (verified after lock)"
    release_lock
    trap - EXIT
    return 0
  fi

  # Create temp directory
  temp_dir=$(mktemp -d)

  # Download to temp file
  temp_archive="$temp_dir/archive"
  download_file "$download_url" "$temp_archive"

  # Verify checksum
  actual_checksum=$(compute_checksum "$temp_archive")
  if [ "$actual_checksum" != "$expected_checksum" ]; then
    rm -f "$temp_archive"
    die "Checksum mismatch for $download_url
Expected: $expected_checksum
Actual:   $actual_checksum"
  fi

  info "Checksum verified: $actual_checksum"

  # Detect archive structure
  local structure
  structure=$(get_archive_structure "$temp_archive" "$download_url")

  # Remove old target if exists
  rm -rf "$target_dir"

  # Extract archive (strips top-level directory if nested, extracts directly if flat)
  extract_archive "$temp_archive" "$target_dir" "$download_url" "$structure"

  # Validate binary exists
  local binary_path="$target_dir/$TOOL_BINARY_UNIX"
  if [ ! -f "$binary_path" ]; then
    die "Binary not found after extraction: $TOOL_BINARY_UNIX"
  fi
  chmod +x "$binary_path"

  # Write flag file to mark completion with checksum
  echo "$expected_checksum" > "$flag_file"

  # Cleanup
  rm -rf "$temp_dir"
  release_lock
  trap - EXIT

  info "Cached: $target_dir"
}

# Verify all platforms (TOOL_VERIFY_ALL_PLATFORMS mode)
verify_all_platforms() {
  local platforms="LINUX_X64 LINUX_ARM64 WINDOWS_X64 WINDOWS_ARM64 MACOS_X64 MACOS_ARM64"
  local temp_dir
  local result=0
  local platform
  local expected_checksum
  local download_url
  local temp_archive
  local actual_checksum
  local file_size

  temp_dir=$(mktemp -d)
  trap 'rm -rf "$temp_dir"' EXIT

  info "=== Verifying all platforms for $TOOL_NAME $TOOL_VERSION ==="
  info ""

  for platform in $platforms; do
    expected_checksum=$(get_checksum "$platform")
    download_url=$(get_url "$platform")

    info "Platform: $platform"
    info "  URL: $download_url"
    info "  Expected: $expected_checksum"

    if [ -z "$expected_checksum" ] || [ -z "$download_url" ]; then
      info "  Status:   FAIL (not configured - missing checksum or URL)"
      info ""
      result=1
      continue
    fi

    temp_archive="$temp_dir/$platform"

    # Use curl directly (not download_file) to avoid die() exiting the script
    local download_error
    if download_error=$(curl -fsSL -o "$temp_archive" "$download_url" 2>&1); then
      actual_checksum=$(compute_checksum "$temp_archive")
      file_size=$(wc -c < "$temp_archive" | tr -d ' ')

      info "  Actual:   $actual_checksum"
      info "  Size:     $file_size bytes"

      if [ "$actual_checksum" = "$expected_checksum" ]; then
        # Report archive structure type
        local structure
        structure=$(get_archive_structure "$temp_archive" "$download_url")
        if [ "$structure" = "flat" ]; then
          info "  Structure: flat (no top-level directory)"
        else
          local top_level="${structure#nested:}"
          info "  Structure: nested (top-level: $top_level)"
        fi

        # Extract and verify binary exists
        local extract_dir="$temp_dir/${platform}_extract"
        mkdir -p "$extract_dir"
        extract_archive "$temp_archive" "$extract_dir" "$download_url" "$structure"

        local platform_binary
        platform_binary=$(get_binary_for_platform "$platform")
        local binary_path="$extract_dir/$platform_binary"

        if [ -f "$binary_path" ]; then
          info "  Binary:   $platform_binary (found)"
          info "  Status:   PASS"
        else
          info "  Binary:   $platform_binary (NOT FOUND)"
          info "  Status:   FAIL (binary missing)"
          result=1
        fi

        rm -rf "$extract_dir"
      else
        info "  Status:   FAIL (checksum mismatch)"
        result=1
      fi
    else
      info "  Error:    $download_error"
      info "  Status:   FAIL (download failed)"
      result=1
    fi

    rm -f "$temp_archive"
    info ""
  done

  rm -rf "$temp_dir"
  trap - EXIT

  if [ "$result" -eq 0 ]; then
    info "=== All platforms verified successfully ==="
  else
    info "=== Some platforms failed verification ==="
  fi

  return $result
}

# Check if tool is already cached and valid
is_cached() {
  local cache_dir="$1"
  local platform="$2"
  local target_dir="$cache_dir/$TOOL_NAME/$TOOL_VERSION"
  local flag_file="$target_dir/.complete"
  local expected_checksum

  expected_checksum=$(get_checksum "$platform")

  # Check flag file contains correct checksum
  if [ -f "$flag_file" ] && grep -qx "$expected_checksum" "$flag_file" 2>/dev/null; then
    # Verify binary exists
    local binary_path="$target_dir/$TOOL_BINARY_UNIX"
    if [ -x "$binary_path" ]; then
      return 0
    fi
  fi
  return 1
}

# Main
main() {
  # Validate required environment variables
  if [ -z "${TOOL_NAME:-}" ]; then
    die "TOOL_NAME not set"
  fi
  if [ -z "${TOOL_VERSION:-}" ]; then
    die "TOOL_VERSION not set"
  fi
  if [ -z "${TOOL_BINARY_UNIX:-}" ]; then
    die "TOOL_BINARY_UNIX not set"
  fi
  if [ -z "${TOOL_BINARY_WINDOWS:-}" ]; then
    die "TOOL_BINARY_WINDOWS not set"
  fi

  # Verification mode
  if [ "${TOOL_VERIFY_ALL_PLATFORMS:-}" = "1" ]; then
    verify_all_platforms
    exit $?
  fi

  # Normal mode
  local platform
  local cache_dir
  local binary_path

  platform=$(detect_platform)
  cache_dir=$(get_cache_dir)

  # Download if not cached
  if ! is_cached "$cache_dir" "$platform"; then
    download_and_cache "$platform" "$cache_dir"
  fi

  # Get binary path and execute
  binary_path="$cache_dir/$TOOL_NAME/$TOOL_VERSION/$TOOL_BINARY_UNIX"

  if [ ! -x "$binary_path" ]; then
    die "Binary not found or not executable: $binary_path"
  fi

  # Add tool directory to PATH so scripts can find related binaries (e.g., npx needs node)
  export PATH="$(dirname "$binary_path"):$PATH"

  exec "$binary_path" "$@"
}

main "$@"
