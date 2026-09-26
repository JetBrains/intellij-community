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
# The parser (grammar/parser/Mermaid.bnf -> gen/.../parser/, gen/.../psi/) is generated headlessly by
# Grammar-Kit's org.intellij.grammar.Main, so the Grammar-Kit IDE plugin is NOT required. Verified:
# generating from the unchanged .bnf reproduces all 535 committed parser and PSI files byte-for-byte.
#
# Grammar-Kit needs IntelliJ platform classes on its classpath. There is no published light-psi
# artifact, and the gradle-grammarkit-plugin gets them from the surrounding project's platform
# dependency, so this script takes them from the monorepo's own Bazel output instead. Two consequences
# worth knowing:
#
#   * Platform jars only exist inside Bazel runfiles trees, which are populated by a build. If none is
#     found the script says which command to run.
#   * Some third-party fat jars (Dokka's analysis-kotlin-descriptors) bundle a stale copy of the
#     IntelliJ platform and will shadow the real classes, which surfaces as a confusing
#     NoSuchMethodError in Conditions.equalTo. They are excluded, and platform jars are put first.
#
# Prerequisites:
#   - JDK 25+ (platform classes are Java 25 / class file 69); a JBR is picked up automatically
#   - Internet access on first run (downloads JFlex and Grammar-Kit; cached in TMPDIR afterwards)
#   - For the parser only: a prior Bazel build, e.g.
#       ./tests.cmd --module intellij.mermaid.tests --test com.intellij.mermaid.lang.lexer.SankeyTest
#
# Usage:
#   ./updateMermaidGrammar.sh              # regenerate lexer and parser
#   ./updateMermaidGrammar.sh lexer        # lexer only
#   ./updateMermaidGrammar.sh parser       # parser only
#   JFLEX_JAR=... GRAMMAR_KIT_JAR=... ./updateMermaidGrammar.sh   # use local jars instead

set -euo pipefail

JFLEX_VERSION="1.9.1"
JFLEX_URL="https://cache-redirector.jetbrains.com/intellij-dependencies/org/jetbrains/intellij/deps/jflex/jflex/$JFLEX_VERSION/jflex-$JFLEX_VERSION.jar"

GRAMMAR_KIT_VERSION="2023.3.1"
GRAMMAR_KIT_URL="https://github.com/JetBrains/Grammar-Kit/releases/download/$GRAMMAR_KIT_VERSION/grammar-kit-$GRAMMAR_KIT_VERSION.zip"

MODE="${1:-all}"
case "$MODE" in
  all|lexer|parser) ;;
  *) echo "Error: unknown mode '$MODE'. Use: all | lexer | parser" >&2; exit 1 ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMMUNITY_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
CACHE_DIR="${TMPDIR:-/tmp}/intellij-mermaid-grammar"
FD="$COMMUNITY_DIR/tools/fd.cmd"

FLEX_FILE="$SCRIPT_DIR/grammar/lexer/MermaidLexer.flex"
SKELETON="$COMMUNITY_DIR/tools/lexer/idea-flex.skeleton"
GEN_DIR="$SCRIPT_DIR/gen/com/intellij/mermaid/lang/lexer"
GENERATED_FILE="$GEN_DIR/_MermaidLexer.java"

BNF_FILE="$SCRIPT_DIR/grammar/parser/Mermaid.bnf"
GEN_ROOT="$SCRIPT_DIR/gen"
PARSER_DIR="$GEN_ROOT/com/intellij/mermaid/lang/parser"
PSI_DIR="$GEN_ROOT/com/intellij/mermaid/lang/psi"

# Path recorded in the generated file's "// source:" header line. JFlex writes it relative to the
# invocation directory, so without pinning it the same content would produce a different header
# depending on where the script was run from. Keep it relative to the plugin directory so it is stable
# in both the monorepo and the standalone community repo. (The original committed lexer carried an
# absolute pre-monorepo path from another machine, which is exactly what this avoids.)
RELATIVE_FLEX_PATH="grammar/lexer/MermaidLexer.flex"

cd "$SCRIPT_DIR"

# Resolve a JDK. The lexer only needs 17+, but the platform classes the parser generator loads are
# compiled for Java 25 (class file 69), so prefer a 25 JBR and require it for parser generation.
resolve_java() {
  local min="$1"
  if [ -n "${JAVA_HOME:-}" ]; then
    echo "$JAVA_HOME/bin/java"; return
  fi
  if command -v /usr/libexec/java_home &>/dev/null; then
    local home
    home=$(/usr/libexec/java_home -v "$min" 2>/dev/null || true)
    if [ -n "$home" ]; then echo "$home/bin/java"; return; fi
  fi
  command -v java || true
}

java_major() {
  "$1" -version 2>&1 | head -1 | sed -E 's/.*"([0-9]+).*/\1/'
}

JAVA_BIN="$(resolve_java 25)"
if [ -z "$JAVA_BIN" ] || [ ! -x "$JAVA_BIN" ]; then
  echo "Error: no JDK found. Set JAVA_HOME." >&2
  exit 1
fi

generate_lexer() {
  [ -f "$FLEX_FILE" ] || { echo "Error: not found: $FLEX_FILE" >&2; exit 1; }
  [ -f "$SKELETON" ] || { echo "Error: not found: $SKELETON" >&2; exit 1; }

  if [ -z "${JFLEX_JAR:-}" ]; then
    JFLEX_JAR="$CACHE_DIR/jflex-$JFLEX_VERSION.jar"
    if [ ! -f "$JFLEX_JAR" ]; then
      echo "Downloading JFlex $JFLEX_VERSION..."
      mkdir -p "$CACHE_DIR"
      curl -sSL --fail -o "$JFLEX_JAR.tmp" "$JFLEX_URL"
      mv "$JFLEX_JAR.tmp" "$JFLEX_JAR"
    fi
  elif [ ! -f "$JFLEX_JAR" ]; then
    echo "Error: JFLEX_JAR does not exist: $JFLEX_JAR" >&2; exit 1
  fi
  echo "Using JFlex $JFLEX_VERSION: $JFLEX_JAR"

  mkdir -p "$GEN_DIR"
  echo "Generating lexer from grammar/lexer/MermaidLexer.flex..."
  "$JAVA_BIN" -jar "$JFLEX_JAR" --skel "$SKELETON" --nobak -d "$GEN_DIR" "$FLEX_FILE"

  [ -f "$GENERATED_FILE" ] || { echo "Error: JFlex did not produce $GENERATED_FILE" >&2; exit 1; }

  # Pin the "// source:" header line so the output does not depend on the invocation directory.
  local escaped
  escaped=$(printf '%s\n' "$RELATIVE_FLEX_PATH" | sed 's/[&/\]/\\&/g')
  sed -i.bak "s|^// source: .*|// source: $escaped|" "$GENERATED_FILE"
  rm -f "$GENERATED_FILE.bak"

  if ! head -5 "$GENERATED_FILE" | grep -q "^// source: $RELATIVE_FLEX_PATH$"; then
    echo "Error: could not normalize the '// source:' header in $GENERATED_FILE" >&2; exit 1
  fi
  if grep -qE '/(Users|home)/' "$GENERATED_FILE"; then
    echo "Error: machine-specific absolute path leaked into $GENERATED_FILE" >&2; exit 1
  fi
  echo "  lexer: $(wc -l < "$GENERATED_FILE" | tr -d ' ') lines"
}

resolve_grammar_kit_jar() {
  if [ -n "${GRAMMAR_KIT_JAR:-}" ]; then
    [ -f "$GRAMMAR_KIT_JAR" ] || { echo "Error: GRAMMAR_KIT_JAR does not exist: $GRAMMAR_KIT_JAR" >&2; exit 1; }
    return
  fi
  GRAMMAR_KIT_JAR="$CACHE_DIR/grammar-kit-$GRAMMAR_KIT_VERSION.jar"
  if [ ! -f "$GRAMMAR_KIT_JAR" ]; then
    echo "Downloading Grammar-Kit $GRAMMAR_KIT_VERSION..."
    mkdir -p "$CACHE_DIR/gk-unpack"
    curl -sSL --fail -o "$CACHE_DIR/gk.zip" "$GRAMMAR_KIT_URL"
    unzip -q -o "$CACHE_DIR/gk.zip" -d "$CACHE_DIR/gk-unpack"
    local jar
    jar="$CACHE_DIR/gk-unpack/grammar-kit/lib/grammar-kit-$GRAMMAR_KIT_VERSION.jar"
    [ -f "$jar" ] || { echo "Error: grammar-kit jar not found inside the release zip" >&2; exit 1; }
    cp "$jar" "$GRAMMAR_KIT_JAR"
    rm -rf "$CACHE_DIR/gk.zip" "$CACHE_DIR/gk-unpack"
  fi
}

# Grammar-Kit needs IntelliJ platform classes. Take them from the monorepo's own Bazel output: find a
# runfiles tree that contains the platform util jar. Populated by any build of a target that depends on
# the platform, e.g. running the mermaid tests once.
find_platform_runfiles() {
  local bazel_bin
  bazel_bin="$(cd "$COMMUNITY_DIR/.." && pwd)/out/bazel-bin"
  [ -e "$bazel_bin" ] || return 1
  bazel_bin="$(cd "$bazel_bin" && pwd)"
  local marker
  marker=$("$FD" -H -t d --max-depth 4 '\.runfiles$' "$bazel_bin" 2>/dev/null | while read -r d; do
    if [ -f "$d/community+/platform/util/util.jar" ]; then echo "$d"; break; fi
  done)
  [ -n "$marker" ] || return 1
  echo "$marker"
}

generate_parser() {
  [ -f "$BNF_FILE" ] || { echo "Error: not found: $BNF_FILE" >&2; exit 1; }

  local major
  major="$(java_major "$JAVA_BIN")"
  if [ "${major:-0}" -lt 25 ]; then
    echo "Error: parser generation needs JDK 25+ (platform classes are class file 69); found $major." >&2
    echo "       Unset JAVA_HOME or point it at a JBR 25." >&2
    exit 1
  fi

  local runfiles
  if ! runfiles="$(find_platform_runfiles)"; then
    echo "Error: no Bazel runfiles tree with IntelliJ platform jars found." >&2
    echo "       Populate it with one build, then re-run:" >&2
    echo "         ./tests.cmd --module intellij.mermaid.tests --test com.intellij.mermaid.lang.lexer.SankeyTest" >&2
    exit 1
  fi

  resolve_grammar_kit_jar
  echo "Using Grammar-Kit $GRAMMAR_KIT_VERSION: $GRAMMAR_KIT_JAR"

  local work jars
  work="$CACHE_DIR/cp"
  mkdir -p "$work"
  jars="$work/jars.txt"

  # Exclude *_test_lib jars (duplicate classes) and third-party fat jars that bundle a stale copy of
  # the IntelliJ platform -- Dokka's analysis-kotlin-descriptors shadows platform util and shows up as
  # NoSuchMethodError in Conditions.equalTo. Platform jars go first so the real classes always win.
  "$FD" -H -e jar . "$runfiles" 2>/dev/null \
    | grep -v '_test_lib\.jar$' \
    | grep -vE 'analysis-kotlin|dokka|kotlin-compiler|-all\.jar$' > "$jars.raw"
  grep '/platform/' "$jars.raw" > "$jars" || true
  grep -v '/platform/' "$jars.raw" >> "$jars" || true

  local argfile ide_home
  argfile="$work/argfile.txt"
  ide_home="$work/ide-home"
  mkdir -p "$ide_home/config" "$ide_home/system" "$ide_home/plugins"
  {
    printf -- '-cp\n'
    printf '%s\n' "$GRAMMAR_KIT_JAR:$(tr '\n' ':' < "$jars")"
  } > "$argfile"

  # Regenerate from scratch so rules deleted from the .bnf do not leave stale PSI files behind. Only
  # the two Grammar-Kit-owned subtrees are cleared; gen/.../lexer and gen/.../icons come from other
  # generators and must survive.
  rm -rf "$PARSER_DIR" "$PSI_DIR"

  echo "Generating parser from grammar/parser/Mermaid.bnf..."
  "$JAVA_BIN" \
    -Didea.home.path="$ide_home" \
    -Didea.config.path="$ide_home/config" \
    -Didea.system.path="$ide_home/system" \
    -Didea.plugins.path="$ide_home/plugins" \
    "@$argfile" org.intellij.grammar.Main "$GEN_ROOT" "$BNF_FILE" \
    2>&1 | grep -vE '^WARNING|^WARN:|TelemetryManager|^\s+at |^java\.lang\.Throwable' || true

  for f in "$PARSER_DIR/_MermaidParser.java" "$PARSER_DIR/MermaidElements.java"; do
    [ -f "$f" ] || { echo "Error: Grammar-Kit did not produce $f" >&2; exit 1; }
  done
  echo "  parser: $(ls "$PSI_DIR"/*.java "$PSI_DIR"/impl/*.java "$PARSER_DIR"/*.java 2>/dev/null | wc -l | tr -d ' ') files"
}

case "$MODE" in
  lexer)  generate_lexer ;;
  parser) generate_parser ;;
  all)    generate_lexer; generate_parser ;;
esac

echo ""
echo "Done. Review the diff, then run the tests:"
echo "  ./tests.cmd --module intellij.mermaid.tests --test 'com.intellij.mermaid.*'"
