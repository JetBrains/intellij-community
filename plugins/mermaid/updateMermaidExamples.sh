#!/bin/bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Refreshes the upstream documentation examples used by OfficialExamplesParsingTest and
# OfficialExamplesRenderTest.
#
# Every ```mermaid-example and ```mermaid fenced block in the mermaid docs is extracted to
#   testData/com/intellij/mermaid/test/examples/<doc>/<doc>-<index>.mermaid
#
# Both fence tags are taken because upstream is inconsistent in its own docs: `mermaid-example` is the
# convention, but some pages use plain `mermaid` (ishikawa.md uses it exclusively, so keying only on
# `mermaid-example` would silently give that family zero coverage).
# That path is not arbitrary: testData is a java-test-resource root (resource_strip_prefix = "testData"
# in BUILD.bazel), so the files land on the test classpath at com/intellij/mermaid/test/examples, which
# is where OfficialDocumentationExamples.obtainBasePath() looks them up via getResource("examples").
#
# The version is whatever gradle.properties pins, so the examples always match the bundled renderer.
# Re-run this after changing mermaidVersion.
#
# This replaces the Gradle :examples:test-data project, which is unusable for two reasons: its output
# never reaches the Bazel/JPS test classpath (so the tests silently self-skipped), and it downloads
# refs/tags/v$version, a tag scheme mermaid abandoned after v11.0.0 -- releases are now tagged
# mermaid@$version, so that download 404s for every version we actually ship.
#
# Prerequisites:
#   - Internet access on first run (the source archive is cached in TMPDIR per version)
#
# Usage:
#   ./updateMermaidExamples.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

MERMAID_VERSION=$(grep '^mermaidVersion' gradle.properties | cut -d'=' -f2 | tr -d ' ')
if [ -z "$MERMAID_VERSION" ]; then
  echo "Error: could not read mermaidVersion from gradle.properties" >&2
  exit 1
fi

CACHE_DIR="${TMPDIR:-/tmp}/intellij-mermaid-examples"
ARCHIVE="$CACHE_DIR/mermaid-$MERMAID_VERSION.zip"
UNPACKED="$CACHE_DIR/unpacked-$MERMAID_VERSION"
OUT_DIR="$SCRIPT_DIR/testData/com/intellij/mermaid/test/examples"

echo "Refreshing documentation examples for mermaid $MERMAID_VERSION"

if [ ! -f "$ARCHIVE" ]; then
  echo "Downloading mermaid $MERMAID_VERSION sources..."
  mkdir -p "$CACHE_DIR"
  curl -sSL --fail -o "$ARCHIVE.tmp" \
    "https://github.com/mermaid-js/mermaid/archive/refs/tags/mermaid@$MERMAID_VERSION.zip"
  mv "$ARCHIVE.tmp" "$ARCHIVE"
fi

rm -rf "$UNPACKED"
mkdir -p "$UNPACKED"
unzip -q -o "$ARCHIVE" '*/packages/mermaid/src/docs/syntax/*' -d "$UNPACKED"

SYNTAX_DIR=$(/bin/ls -d "$UNPACKED"/*/packages/mermaid/src/docs/syntax 2>/dev/null | head -1)
if [ -z "$SYNTAX_DIR" ] || [ ! -d "$SYNTAX_DIR" ]; then
  echo "Error: docs/syntax not found in the archive; did the upstream layout change?" >&2
  exit 1
fi

# Regenerate from scratch so examples deleted upstream do not linger.
rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

total=0
docs=0
for md in "$SYNTAX_DIR"/*.md; do
  [ -f "$md" ] || continue
  name=$(basename "$md" .md)
  dir="$OUT_DIR/$name"
  mkdir -p "$dir"
  # Extract every ```mermaid-example / ```mermaid block. A bare ``` closes the current block.
  awk -v dir="$dir" -v name="$name" '
    /^[ \t]*```(mermaid|mermaid-example)[ \t]*$/ { inblk = 1; out = sprintf("%s/%s-%d.mermaid", dir, name, n++); next }
    inblk && /^[ \t]*```[ \t]*$/                 { inblk = 0; close(out); next }
    inblk                                        { print > out }
  ' "$md"
  count=$(/bin/ls "$dir" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    rmdir "$dir"
  else
    docs=$((docs + 1))
    total=$((total + count))
    printf '  %-32s %s\n' "$name" "$count"
  fi
done

echo ""
echo "Done. Extracted $total examples from $docs documents into"
echo "  testData/com/intellij/mermaid/test/examples/"
echo "Run the conformance tests:"
echo "  ./tests.cmd --module intellij.mermaid.tests --test com.intellij.mermaid.lang.preview.OfficialExamplesParsingTest"
