#!/bin/bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Updates the bundled mermaid.js, mermaid.js.map, and mermaid.css files.
#
# Prerequisites:
#   - JDK 17+ available (set JAVA_HOME or have it on PATH)
#   - Internet access (downloads Node.js, npm packages, and Gradle dependencies)
#
# Usage:
#   ./updateMermaidJs.sh                    # build with current mermaidVersion from gradle.properties
#   ./updateMermaidJs.sh 11.15.0            # build with a specific mermaid version

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RESOURCES_DIR="$SCRIPT_DIR/resources/com/intellij/mermaid/markdown/jcef"
DIST_DIR="$SCRIPT_DIR/browser/extension/build/dist/js/productionExecutable"

# Resolve JDK 17+ for Gradle
if [ -z "${JAVA_HOME:-}" ]; then
  if command -v /usr/libexec/java_home &>/dev/null; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null || true)
  fi
  if [ -z "${JAVA_HOME:-}" ]; then
    echo "Error: JAVA_HOME is not set and no JDK 17+ found. Set JAVA_HOME to a JDK 17+ installation." >&2
    exit 1
  fi
fi
export JAVA_HOME
echo "Using JAVA_HOME=$JAVA_HOME"

cd "$SCRIPT_DIR"

# Update mermaid version if provided as argument
REQUESTED_VERSION="${1:-}"
if [ -n "$REQUESTED_VERSION" ]; then
  echo "Updating mermaidVersion to $REQUESTED_VERSION in gradle.properties"
  sed -i.bak "s/^mermaidVersion = .*/mermaidVersion = $REQUESTED_VERSION/" gradle.properties
  rm -f gradle.properties.bak
fi

MERMAID_VERSION=$(grep '^mermaidVersion' gradle.properties | cut -d'=' -f2 | tr -d ' ')
echo "Building mermaid.js bundle for mermaid version $MERMAID_VERSION"

# Clean previous build artifacts to avoid stale caches
rm -rf browser/extension/build/dist
rm -rf build/js

echo "Running Gradle build..."
./gradlew :browser:extension:browserDistribution --quiet -PuseNodeMirror=false -PautomatedProductionBuild=true

# Copy results to resources
echo "Copying bundle to resources..."
mkdir -p "$RESOURCES_DIR"
cp "$DIST_DIR/mermaid.js" "$RESOURCES_DIR/mermaid.js"
cp "$DIST_DIR/mermaid.js.map" "$RESOURCES_DIR/mermaid.js.map"
cp "$SCRIPT_DIR/browser/extension/src/main/resources/mermaid.css" "$RESOURCES_DIR/mermaid.css"

# Clean Gradle build outputs (they conflict with Bazel on case-insensitive filesystems)
echo "Cleaning Gradle build outputs..."
rm -rf build .gradle kotlin-js-store
rm -rf browser/extension/build browser/mermaid-api/build
rm -rf buildSrc/build

JS_SIZE=$(wc -c < "$RESOURCES_DIR/mermaid.js" | tr -d ' ')
MAP_SIZE=$(wc -c < "$RESOURCES_DIR/mermaid.js.map" | tr -d ' ')

echo ""
echo "Done! Updated mermaid.js bundle (mermaid $MERMAID_VERSION):"
echo "  mermaid.js     : $JS_SIZE bytes"
echo "  mermaid.js.map : $MAP_SIZE bytes"
echo "  mermaid.css    : $(wc -c < "$RESOURCES_DIR/mermaid.css" | tr -d ' ') bytes"