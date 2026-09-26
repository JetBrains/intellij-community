#!/bin/bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "$0")" && pwd)
cd "$SCRIPT_DIR"

if [[ $# -eq 1 && "$1" == "--help" ]]; then
  echo "Usage: ./loadVSCBundles.sh [--dry-run]"
  echo "Refresh the bundled TextMate grammars under lib/bundles."
  echo
  echo "The script downloads upstream bundle sources, applies bundles.patch,"
  echo "rewrites package metadata, and minifies JSON output."
  echo
  echo "Options:"
  echo "  --dry-run   Prepare everything in staging but do not replace lib/bundles."
  exit 0
fi

exec zsh "$SCRIPT_DIR/../../tools/kotlin.cmd" "$SCRIPT_DIR/loadVSCBundles.main.kts" -- "$@"
