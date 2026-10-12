:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# `vm` - the Air UI-lane controller, running the Rust binary Bazel builds
# (`@community//tools/vm:vm`, one file, always optimized).
#
# Same dual sh/CMD polyglot shape as bt.cmd. Seven decisions shape it: bt.cmd shares 1 and 2, and 3 to 7
# are where the two differ. Each is a decision, not a simplification:
#
# 1. **No cache, and no version key**, as in bt.cmd since it stopped wrapping a pinned bun. This wrapper
#    resolves a binary built from working-copy sources, which change on every edit and are keyed by
#    nothing. There is no honest cache key, and the
#    failure a wrong one produces is this port's worst case: a silently stale controller judging a lane
#    and reporting a verdict about code it did not run.
#    `vm/docs/decisions/0105-the-ui-lane-controller-is-go.md` (superseded by ADR 0059, the port to Rust)
#    records the same trap already paid for once - an `lstat`-based freshness check on a `go_cross_binary`
#    degraded to "always reinstall", passed its whole suite, and failed on hardware. So every invocation runs
#    `bazel run --script_path`: the build *is* the freshness guarantee.
#
#    The cost is ~1.3 s of analysis, warm. A lane iteration is ~196 s, and even `status` reaches a
#    hypervisor. Against that this is noise - and unlike bt, this tool cannot function without a working
#    host Bazel anyway, because preparing a build is what `run`, `shard` and `flake` do first.
#
# 2. **`AIR_VM_BIN` overrides everything on the sh half, checked first.** It names an explicit binary and
#    skips Bazel entirely: a debugger attaching to it, a hand-built binary, or one binary held for the whole of a
#    comparison rather than rebuilt between its halves. There is no parity gate behind it any more; ADR
#    0174 deleted the Go implementation it compared against. The CMD half reads it the same way (note 7).
#
# 3. **No `--refresh`.** There is nothing to drop.
#
# 4. **Wrapper failures exit 78, where bt.cmd's exit 6.** Both wrappers report their own infrastructure
#    failure - no Bazel, no binary - with a code the tool they launch cannot produce, so a caller can tell
#    "the tool did not start" from "the tool answered". For BT that code *is* 6, because its `exit::INFRA`
#    is 6 and means the same thing: the run said nothing about the tests. For this controller 6 is
#    `Exit::TESTS_FAILED`, a verdict - a red lane leaves with it - so 6 here would make a build failure
#    indistinguishable from a red lane. 78 is `EX_CONFIG` and the one sysexits code the controller does not
#    use (it uses 1, 2, 6, 65, 66, 69, 70, 73, 75, 77). **The asymmetry is deliberate; do not harmonize
#    the two wrappers on one number.**
#
# 5. **`--config=air-lane-linux` on the build.** The controller's own lane builds pass the same config
#    (`host_bazel_argv` in `vm/crates/avl-vm/src/lane/bazel.rs`), and its options are part of Bazel's
#    analysis key. Without it, each call of this wrapper discards the analysis of the last lane build. It is
#    the config of the default guest, so a lane build for a macOS guest still pays one analysis.
#
# 6. **Where the build's output goes depends on who reads stderr.** For a pipe, it goes to a log file and
#    reaches stderr only when the build fails. A refusal envelope goes to stderr, so a Bazel line there
#    (`WARNING: Build options ... have changed`) came before the envelope, and `jq` on stderr failed. A green
#    build says nothing a caller needs, so it says nothing. For a terminal, the output goes straight to stderr:
#    a person then sees a cold build of the controller move, which is the one phase the controller cannot
#    report, because it does not exist yet. The CMD half cannot tell a terminal from a pipe, so it always
#    takes the pipe path: the output goes to a log file, and reaches stderr only when the build fails.
#
# 7. **The CMD half runs the controller natively.** A Windows host drives only the Docker backend, through
#    `docker.exe`, and the controller refuses a Tart or a Parallels selection there. The CMD half follows the
#    sh half step for step, with the shapes of the CMD half of bt.cmd: the root from `%~dp0`, the arguments
#    collected one at a time, and the binary from the first token of the launcher's last line. It has no
#    `readlink -f`, because the launcher of a Windows `bazel run` names the binary itself.
#
# Everything else is bt.cmd's, for bt.cmd's reasons: the bash pinning, reading the launcher's last line,
# the `readlink -f` fallback, and exporting BUILD_WORKSPACE_DIRECTORY while unsetting any leaked RUNFILES_*.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter - dash on Ubuntu, bash on
# macOS. Pin it, as tests.cmd and bt.cmd do, rather than leaving the wrapper's behaviour to the caller.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

# Resolve the Community root in either layout, then use its parent only for an Ultimate checkout, as bt.cmd does. In a
# Community checkout the controller refuses with `air_area_missing`, because no `bt.json` there names the Air area.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
community_root="$(cd -- "$self_dir/.." && pwd)"
parent="$(cd -- "$community_root/.." && pwd)"
root="$community_root"
target="//tools/vm:vm"
if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
   [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
  root="$parent"
  target="@community//tools/vm:vm"
fi

# BUILD_WORKSPACE_DIRECTORY is how the controller finds `bt.json` and `community/tools/vm/provision` - a Bazel-built
# binary's own path is inside a Bazel output tree and says nothing about the checkout. Any RUNFILES_* in
# the environment leaked from an outer invocation and would only mislead a runfiles lookup.
export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

# The override is checked before Bazel is consulted at all: naming a binary that is not there is a
# mistake worth reporting, not a reason to quietly build a different one.
if [ -n "${AIR_VM_BIN:-}" ]; then
  if [ ! -x "$AIR_VM_BIN" ]; then
    echo "vm: AIR_VM_BIN is not an executable file: $AIR_VM_BIN" >&2
    exit 78
  fi
  exec "$AIR_VM_BIN" "$@"
fi

mkdir -p "$root/out/air"
launcher="$root/out/air/vm.launcher.$$"
build_log="$root/out/air/vm.build.$$.log"
# `--config=bt` (`community/common.bazelrc`) keeps Bazel's progress and result lines short. For a pipe, the log file keeps
# the rest out of both envelope streams; a terminal sees it (note 6 above). A failure here is infrastructure,
# which this wrapper reports as 78 - the one code no controller command uses (note 4 above).
if [ -t 2 ]; then
  if ! (cd "$root" && ./bazel.cmd run --config=bt --config=air-lane-linux --script_path="$launcher" "$target" >&2); then
    rm -f "$launcher"
    echo "vm: could not build $target; see the bazel output above" >&2
    exit 78
  fi
elif ! (cd "$root" && ./bazel.cmd run --config=bt --config=air-lane-linux --script_path="$launcher" "$target" >"$build_log" 2>&1); then
  cat "$build_log" >&2
  rm -f "$launcher" "$build_log"
  echo "vm: could not build $target; see the bazel output above" >&2
  exit 78
fi
rm -f "$build_log"

# `bazel run --script_path` writes `<binary> <args> "$@"` as the last line; only its first token is
# wanted. Reading it beats adding a second way to ask Bazel where its output went.
resolved="$(awk 'END { print $1 }' "$launcher")"
rm -f "$launcher"
# The launcher points at a symlink under the output base; resolving through it reaches the real file.
# Where readlink -f is unavailable the unresolved path still works.
resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s' "$resolved")"
if [ ! -x "$resolved" ]; then
  echo "vm: could not resolve the controller from $target (got '$resolved')" >&2
  exit 78
fi

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM The same steps as the sh half, see note 7. The launcher is only read for its last line and never run.

for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "PARENT=%%~fd"
for %%d in ("%PARENT%\community") do set "PARENT_COMMUNITY=%%~fd"
set "ROOT=%COMMUNITY_ROOT%"
set "TARGET=//tools/vm:vm"
if not exist "%PARENT%\MODULE.bazel" goto :ROOTREADY
if not exist "%PARENT%\bazel.cmd" goto :ROOTREADY
if not exist "%PARENT_COMMUNITY%\" goto :ROOTREADY
if /I not "%PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :ROOTREADY
set "ROOT=%PARENT%"
set "TARGET=@community//tools/vm:vm"
:ROOTREADY

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

if defined AIR_VM_BIN (
  if not exist "%AIR_VM_BIN%" (
    echo vm: AIR_VM_BIN is not an executable file: %AIR_VM_BIN% 1>&2
    exit /B 78
  )
  "%AIR_VM_BIN%"%ARGS%
  exit /B %ERRORLEVEL%
)

if not exist "%ROOT%\out\air" mkdir "%ROOT%\out\air"
REM One %RANDOM% read, reused: two reads would produce two different names.
set "STAMP=%RANDOM%"
set "LAUNCHER=%ROOT%\out\air\vm.launcher.%STAMP%"
set "BUILD_LOG=%ROOT%\out\air\vm.build.%STAMP%.log"
REM The pipe path of note 6: the build output reaches stderr only when the build fails.
pushd "%ROOT%"
call bazel.cmd run --config=bt --config=air-lane-linux --script_path="%LAUNCHER%" "%TARGET%" >"%BUILD_LOG%" 2>&1
set "STATUS=%ERRORLEVEL%"
popd
if not "%STATUS%"=="0" (
  type "%BUILD_LOG%" 1>&2
  if exist "%LAUNCHER%" del /q "%LAUNCHER%"
  del /q "%BUILD_LOG%"
  echo vm: could not build %TARGET%; see the bazel output above 1>&2
  exit /B 78
)
del /q "%BUILD_LOG%"

REM Only the last line is wanted - `<binary> <args> %*` - so the loop deliberately overwrites.
set "RESOLVED="
for /f "usebackq tokens=1" %%b in ("%LAUNCHER%") do set "RESOLVED=%%~b"
del /q "%LAUNCHER%"
if not defined RESOLVED goto :RESOLVEFAILED
if not exist "%RESOLVED%" goto :RESOLVEFAILED

"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%

:RESOLVEFAILED
echo vm: could not resolve the controller from %TARGET% ^(got '%RESOLVED%'^) 1>&2
exit /B 78
