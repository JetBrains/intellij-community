:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# `BT` - the Bazel test wrapper, running the Rust binary Bazel builds (`community/tools/bt`, one file, always
# optimized): `@community//tools/bt:bt` in an ultimate checkout, `//tools/bt:bt` in a community-only one.
#
# It lives in `community/tools` so that every tree of the monorepo runs it from one path, and so does its source,
# so both checkout layouts build it. `bt.json` at the checkout root names the areas it resolves in.
#
# Same shape as `plugins/air/scripts/vm.cmd`, for the same reasons, and the differences from the bun wrapper
# this replaced on 2026-08-24 are decisions rather than simplifications.
# `plugins/air/docs/decisions/0107-bt-is-the-go-binary.md` records them, and ADR 0174 keeps them for the Rust
# binary; the two that change what a caller sees:
#
# 1. **No cache, and no version key.** The old wrapper cached the one fact `bazel run` establishes, because
#    what it resolved was a *pinned* bun: a version bump changed the cache file's name, so a stale entry
#    became unreachable rather than wrong. This wrapper resolves a binary built from working-copy sources,
#    which change on every edit and are keyed by nothing. There is no honest cache key, and a wrong one is
#    silent: BT would report a verdict about test-selection code it did not run. So every invocation runs
#    `bazel run --script_path` and the build *is* the freshness guarantee. ADR 0105 records that trap already
#    paid for once - an `lstat` freshness check that degraded to "always reinstall", passed its whole suite,
#    and failed on hardware.
#
#    The cost is ~1.3 s of analysis, warm, on a command whose cheapest useful run is a bazel test invocation.
#    What it also costs is ADR 0017's one genuine property: a cached path kept BT running when
#    `--lockfile_mode=error` took every other Bazel command down, so BT could report the breakage. Now a
#    broken MODULE.bazel takes BT with it - which is why the exit-6 message below leaves Bazel's own output
#    on stderr rather than summarizing it.
#
# 2. **`AIR_BT_BIN` overrides everything, checked before Bazel is consulted at all.** It names an explicit
#    binary and skips the build entirely: a debugger attaching to it, a hand-built binary, or one binary held
#    for the whole of a comparison rather than rebuilt between its halves. Mirrors `AIR_VM_BIN`.
#
# `--refresh` is gone: there is nothing to drop. It is refused loudly rather than ignored, because an agent
# passing it would otherwise have its first argument silently swallowed and get a different run than it asked
# for. The refusal comes before even `AIR_BT_BIN`, because it is a statement about the caller's argv rather
# than about where the binary comes from: one message answers it however BT would have been resolved.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter - dash on Ubuntu, bash on
# macOS. Pin it, as tests.cmd and vm.cmd do, rather than leaving the wrapper's behaviour to the caller.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

# Resolve the Community root in either layout, then use its parent only for an Ultimate checkout. `root` reaches
# the binary as BUILD_WORKSPACE_DIRECTORY, which is what every path BT builds is relative to.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
community_root="$(cd -- "$self_dir/.." && pwd)"
parent="$(cd -- "$community_root/.." && pwd)"
root="$community_root"
target="//tools/bt:bt"
if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
   [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
  root="$parent"
  target="@community//tools/bt:bt"
fi

if [ $# -gt 0 ] && [ "$1" = "--refresh" ]; then
  echo "bt: --refresh no longer exists; the wrapper rebuilds on every invocation" >&2
  exit 2
fi

# BUILD_WORKSPACE_DIRECTORY is how BT finds the checkout - a Bazel-built binary's own path is inside a Bazel
# output tree and says nothing about it, and the binary refuses rather than guessing. Any RUNFILES_* in the
# environment leaked from an outer invocation and would only mislead a runfiles lookup.
export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

# The override is checked before Bazel is consulted at all: naming a binary that is not there is a mistake
# worth reporting, not a reason to quietly build a different one.
if [ -n "${AIR_BT_BIN:-}" ]; then
  if [ ! -x "$AIR_BT_BIN" ]; then
    echo "bt: AIR_BT_BIN is not an executable file: $AIR_BT_BIN" >&2
    exit 6
  fi
  exec "$AIR_BT_BIN" "$@"
fi

mkdir -p "$root/out/air"
launcher="$root/out/air/bt.launcher.$$"
# `--config=bt` (`community/common.bazelrc`) keeps Bazel's progress and result lines out of the wrapper's output contract,
# and `>&2` keeps whatever is left off stdout, where a `--json` run writes exactly one payload. A failure
# here is infrastructure, which this wrapper reports as 6 - BT's own code for a run that said nothing about
# the tests.
#
# 6 is right *here* and wrong in vm.cmd, which uses 78 for the same class of failure. Both wrappers report
# their own infrastructure failure with a code the tool they launch cannot mistake for a verdict: BT defines
# 6 as `exit::INFRA`, which already means "the run said nothing", so wrapper and binary agree; the VM
# controller defines 6 as `Exit::TESTS_FAILED`, a red lane. **The asymmetry is deliberate; do not harmonize
# the two wrappers on one number.**
if ! (cd "$root" && ./bazel.cmd run --config=bt --script_path="$launcher" "$target" >&2); then
  rm -f "$launcher"
  echo "bt: could not build $target; see the bazel output above" >&2
  exit 6
fi

# `bazel run --script_path` writes `<binary> <args> "$@"` as the last line; only its first token is wanted.
# Reading it beats adding a second way to ask Bazel where its output went.
resolved="$(awk 'END { print $1 }' "$launcher")"
rm -f "$launcher"
# The launcher points at a symlink under the output base; resolving through it reaches the real file. Where
# readlink -f is unavailable the unresolved path still works.
resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s' "$resolved")"
if [ ! -x "$resolved" ]; then
  echo "bt: could not resolve BT from $target (got '$resolved')" >&2
  exit 6
fi

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM Same design as the POSIX half: no cache, AIR_BT_BIN honored first, otherwise resolve through
REM `bazel run --script_path` on every invocation and exec the launcher's binary. The launcher is only read
REM for its last line, never executed, so the Windows formatter's leading `cd /d <runfiles dir>` does not
REM force a separate code path. BT is a developer tool used on Windows, which is why nothing on this
REM target's dependency path may be Windows-incompatible.

for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "PARENT=%%~fd"
for %%d in ("%PARENT%\community") do set "PARENT_COMMUNITY=%%~fd"
set "ROOT=%COMMUNITY_ROOT%"
set "TARGET=//tools/bt:bt"
if not exist "%PARENT%\MODULE.bazel" goto :ROOTREADY
if not exist "%PARENT%\bazel.cmd" goto :ROOTREADY
if not exist "%PARENT_COMMUNITY%\" goto :ROOTREADY
if /I not "%PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :ROOTREADY
set "ROOT=%PARENT%"
set "TARGET=@community//tools/bt:bt"
:ROOTREADY

if "%~1"=="--refresh" (
  echo bt: --refresh no longer exists; the wrapper rebuilds on every invocation 1>&2
  exit /B 2
)

set "BUILD_WORKSPACE_DIRECTORY=%ROOT%"
set "JAVA_RUNFILES="
set "RUNFILES_DIR="
set "RUNFILES_MANIFEST_FILE="
set "RUNFILES_MANIFEST_ONLY="
set "TEST_SRCDIR="

REM `shift` does not rewrite %*, so the forwarded arguments are collected one at a time.
set "ARGS="
:PARSEARGS
if "%~1"=="" goto :PARSED
set "ARGS=%ARGS% %1"
shift
goto :PARSEARGS
:PARSED

if defined AIR_BT_BIN (
  if not exist "%AIR_BT_BIN%" (
    echo bt: AIR_BT_BIN is not an executable file: %AIR_BT_BIN% 1>&2
    exit /B 6
  )
  "%AIR_BT_BIN%"%ARGS%
  exit /B %ERRORLEVEL%
)

if not exist "%ROOT%\out\air" mkdir "%ROOT%\out\air"
REM One %RANDOM% read, reused: two reads would produce two different names.
set "LAUNCHER=%ROOT%\out\air\bt.launcher.%RANDOM%"
pushd "%ROOT%"
call bazel.cmd run --config=bt --script_path="%LAUNCHER%" "%TARGET%" 1>&2
set "STATUS=%ERRORLEVEL%"
popd
if not "%STATUS%"=="0" (
  if exist "%LAUNCHER%" del /q "%LAUNCHER%"
  echo bt: could not build %TARGET%; see the bazel output above 1>&2
  exit /B 6
)

REM Only the last line is wanted - `<binary> <args> %*` - so the loop deliberately overwrites.
set "RESOLVED="
for /f "usebackq tokens=1" %%b in ("%LAUNCHER%") do set "RESOLVED=%%~b"
del /q "%LAUNCHER%"
if not defined RESOLVED goto :RESOLVEFAILED
if not exist "%RESOLVED%" goto :RESOLVEFAILED

"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%

:RESOLVEFAILED
echo bt: could not resolve BT from %TARGET% ^(got '%RESOLVED%'^) 1>&2
exit /B 6
