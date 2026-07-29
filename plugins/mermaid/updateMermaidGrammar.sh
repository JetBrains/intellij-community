#!/bin/bash
# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Regenerates the committed lexer in gen/ from grammar/lexer/MermaidLexer.flex.
#
# The generated sources under gen/ are committed, so run this after every change to the .flex file
# and commit the result together with the grammar change.
#
# JFlex is pinned so that regenerating an unchanged .flex is a no-op: any diff then means the grammar
# really changed, rather than being toolchain noise. Do not bump JFLEX_VERSION casually -- it rewrites
# the whole generated file.
#
# The pin is 1.9.1, matching community/plugins/sh/core/src/com/intellij/sh/lexer/gen_lexer.sh, the
# only other in-repo script driving this skeleton. Note it is NOT the 1.7.0-1 recorded in the
# originally committed lexer: the canonical community/tools/lexer/idea-flex.skeleton assigns zzAtBOL
# without declaring it, relying on JFlex 1.9+ to emit the declaration. Generating with 1.7.0-1 against
# this skeleton therefore does not compile, and the 1.7-era skeleton that produced the original file
# is no longer in the repo. 1.9.x also emits a more compact table encoding, so the first regeneration
# rewrote the file wholesale; subsequent runs are stable.
#
# The parser (grammar/parser/Mermaid.bnf -> gen/.../parser/, gen/.../psi/) is NOT handled here yet;
# it still needs the Grammar-Kit IDE plugin ("Generate Parser Code" on the .bnf).
#
# Prerequisites:
#   - JDK 17+ available (set JAVA_HOME or have java on PATH)
#   - Internet access on first run (downloads the JFlex jar; cached in TMPDIR afterwards)
#
# Usage:
#   ./updateMermaidGrammar.sh              # regenerate the lexer
#   JFLEX_JAR=/path/to/jflex.jar ./updateMermaidGrammar.sh   # use a local JFlex jar instead

set -euo pipefail

JFLEX_VERSION="1.9.1"
JFLEX_URL="https://cache-redirector.jetbrains.com/intellij-dependencies/org/jetbrains/intellij/deps/jflex/jflex/$JFLEX_VERSION/jflex-$JFLEX_VERSION.jar"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMMUNITY_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

FLEX_FILE="$SCRIPT_DIR/grammar/lexer/MermaidLexer.flex"
SKELETON="$COMMUNITY_DIR/tools/lexer/idea-flex.skeleton"
GEN_DIR="$SCRIPT_DIR/gen/com/intellij/mermaid/lang/lexer"
GENERATED_FILE="$GEN_DIR/_MermaidLexer.java"

# Path recorded in the generated file's "// source:" header line. JFlex writes it relative to the
# invocation directory, so without pinning it the same content would produce a different header
# depending on where the script was run from. Keep it relative to the plugin directory so it is stable
# in both the monorepo and the standalone community repo. (The original committed lexer carried an
# absolute pre-monorepo path from another machine, which is exactly what this avoids.)
RELATIVE_FLEX_PATH="grammar/lexer/MermaidLexer.flex"

for f in "$FLEX_FILE" "$SKELETON"; do
  if [ ! -f "$f" ]; then
    echo "Error: required file not found: $f" >&2
    exit 1
  fi
done

cd "$SCRIPT_DIR"

# Resolve JDK
if [ -z "${JAVA_HOME:-}" ]; then
  if command -v /usr/libexec/java_home &>/dev/null; then
    JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || /usr/libexec/java_home -v 21 2>/dev/null || true)
  fi
fi
if [ -n "${JAVA_HOME:-}" ]; then
  export JAVA_HOME
  JAVA_BIN="$JAVA_HOME/bin/java"
else
  JAVA_BIN="$(command -v java || true)"
fi
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "Error: no JDK found. Set JAVA_HOME to a JDK 17+ installation." >&2
  exit 1
fi

# Resolve JFlex jar
if [ -n "${JFLEX_JAR:-}" ]; then
  if [ ! -f "$JFLEX_JAR" ]; then
    echo "Error: JFLEX_JAR is set but does not exist: $JFLEX_JAR" >&2
    exit 1
  fi
else
  CACHE_DIR="${TMPDIR:-/tmp}/intellij-mermaid-grammar"
  JFLEX_JAR="$CACHE_DIR/jflex-$JFLEX_VERSION.jar"
  if [ ! -f "$JFLEX_JAR" ]; then
    echo "Downloading JFlex $JFLEX_VERSION..."
    mkdir -p "$CACHE_DIR"
    curl -sSL --fail -o "$JFLEX_JAR.tmp" "$JFLEX_URL"
    mv "$JFLEX_JAR.tmp" "$JFLEX_JAR"
  fi
fi
echo "Using JFlex $JFLEX_VERSION: $JFLEX_JAR"

mkdir -p "$GEN_DIR"

echo "Generating lexer from grammar/lexer/MermaidLexer.flex..."
"$JAVA_BIN" -jar "$JFLEX_JAR" \
  --skel "$SKELETON" \
  --nobak \
  -d "$GEN_DIR" \
  "$FLEX_FILE"

if [ ! -f "$GENERATED_FILE" ]; then
  echo "Error: JFlex did not produce $GENERATED_FILE" >&2
  exit 1
fi

# Pin the "// source:" header line so the output does not depend on the invocation directory.
echo "Normalizing generated header path..."
ESCAPED_REL_PATH=$(printf '%s\n' "$RELATIVE_FLEX_PATH" | sed 's/[&/\]/\\&/g')
sed -i.bak "s|^// source: .*|// source: $ESCAPED_REL_PATH|" "$GENERATED_FILE"
rm -f "$GENERATED_FILE.bak"

if ! head -5 "$GENERATED_FILE" | grep -q "^// source: $RELATIVE_FLEX_PATH$"; then
  echo "Error: could not normalize the '// source:' header in $GENERATED_FILE" >&2
  exit 1
fi
if grep -qE '/(Users|home)/' "$GENERATED_FILE"; then
  echo "Error: machine-specific absolute path leaked into $GENERATED_FILE" >&2
  exit 1
fi

echo ""
echo "Done! Regenerated $(basename "$GENERATED_FILE") ($(wc -l < "$GENERATED_FILE" | tr -d ' ') lines)."
echo "Review the diff, then run the lexer tests:"
echo "  ./tests.cmd --module intellij.mermaid.tests --test 'com.intellij.mermaid.lang.lexer.*Test'"
