:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# `trace` - the AIR UI-scenario trace tool, running the Rust binary Bazel builds
# (`@community//tools/vm:air-trace`, one file, always optimized): `serve`, `plan` and
# `pack`. The recorder is a separate binary, `:air-trace-record`, which a lane starts itself from its runfiles.
#
# The same dual sh/CMD shape as vm.cmd, for vm.cmd's reasons:
#
# 1. **No cache.** The binary is built from working-copy sources, so every invocation runs
#    `bazel run --script_path` and the build is the freshness guarantee. A stale planner would print commands
#    for a lane layout that no longer exists.
# 2. **`AIR_TRACE_BIN` overrides everything on the sh half, checked first.** It names an explicit binary and
#    skips Bazel, for a debugger attached to a hand-built binary. The CMD half reads it the same way (note 5).
# 3. **Wrapper failures exit 78** (`EX_CONFIG`), a code `air-trace` itself never returns, so "the tool did not
#    start" stays distinguishable from anything the tool answers.
# 4. **`--config=air-lane-linux` on the build**, as in vm.cmd. A call between `vm.cmd daemon warm` and
#    `vm.cmd run` then keeps the analysis that the warm-up made.
# 5. **The CMD half runs `air-trace` natively**, as in vm.cmd. It follows the sh half step for step, with the
#    shapes of the CMD half of bt.cmd, and sends the build output to stderr as the sh half does.
#
# BUILD_WORKSPACE_DIRECTORY is how `plan` and `serve` find the checkout and its `bt.json`, since a Bazel-built
# binary's own path says nothing about it.
#
# `serve --detach` keeps one server for this machine. It returns when the server answers, and it starts the
# server in a new session when none answers. That server writes to `<runtime root>/viewer/serve.log`, builds the
# docs site when the site is missing or old, and stops after 30 minutes with no request and no open event
# stream (`--idle-exit`). The controller starts
# `trace.cmd serve` with the same arguments, in a session of its own, and does not wait for it.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter - dash on Ubuntu, bash on
# macOS. Pin it, as vm.cmd does.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

# Resolve the Community root in either layout, then use its parent only for an Ultimate checkout, as bt.cmd does. In a
# Community checkout `plan` refuses with `air_area_missing`, because no `bt.json` there names the Air area.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
community_root="$(cd -- "$self_dir/.." && pwd)"
parent="$(cd -- "$community_root/.." && pwd)"
root="$community_root"
target="//tools/vm:air-trace"
if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
   [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
  root="$parent"
  target="@community//tools/vm:air-trace"
fi

export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

if [ -n "${AIR_TRACE_BIN:-}" ]; then
  if [ ! -x "$AIR_TRACE_BIN" ]; then
    echo "trace: AIR_TRACE_BIN is not an executable file: $AIR_TRACE_BIN" >&2
    exit 78
  fi
  exec "$AIR_TRACE_BIN" "$@"
fi

mkdir -p "$root/out/air"
launcher="$root/out/air/trace.launcher.$$"
# `--config=bt` keeps Bazel's progress lines quiet and `>&2` keeps the rest off stdout, which `plan` answers on.
if ! (cd "$root" && ./bazel.cmd run --config=bt --config=air-lane-linux --script_path="$launcher" "$target" >&2); then
  rm -f "$launcher"
  echo "trace: could not build $target; see the bazel output above" >&2
  exit 78
fi

# `bazel run --script_path` writes `<binary> <args> "$@"` as the last line; only its first token is wanted.
resolved="$(awk 'END { print $1 }' "$launcher")"
rm -f "$launcher"
resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s' "$resolved")"
if [ ! -x "$resolved" ]; then
  echo "trace: could not resolve air-trace from $target (got '$resolved')" >&2
  exit 78
fi

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM The same steps as the sh half, see note 5. The launcher is only read for its last line and never run.

for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "PARENT=%%~fd"
for %%d in ("%PARENT%\community") do set "PARENT_COMMUNITY=%%~fd"
set "ROOT=%COMMUNITY_ROOT%"
set "TARGET=//tools/vm:air-trace"
if not exist "%PARENT%\MODULE.bazel" goto :ROOTREADY
if not exist "%PARENT%\bazel.cmd" goto :ROOTREADY
if not exist "%PARENT_COMMUNITY%\" goto :ROOTREADY
if /I not "%PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :ROOTREADY
set "ROOT=%PARENT%"
set "TARGET=@community//tools/vm:air-trace"
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

if defined AIR_TRACE_BIN (
  if not exist "%AIR_TRACE_BIN%" (
    echo trace: AIR_TRACE_BIN is not an executable file: %AIR_TRACE_BIN% 1>&2
    exit /B 78
  )
  "%AIR_TRACE_BIN%"%ARGS%
  exit /B %ERRORLEVEL%
)

if not exist "%ROOT%\out\air" mkdir "%ROOT%\out\air"
REM One %RANDOM% read, reused: two reads would produce two different names.
set "LAUNCHER=%ROOT%\out\air\trace.launcher.%RANDOM%"
REM `1>&2` keeps the build output off stdout, which `plan` answers on.
pushd "%ROOT%"
call bazel.cmd run --config=bt --config=air-lane-linux --script_path="%LAUNCHER%" "%TARGET%" 1>&2
set "STATUS=%ERRORLEVEL%"
popd
if not "%STATUS%"=="0" (
  if exist "%LAUNCHER%" del /q "%LAUNCHER%"
  echo trace: could not build %TARGET%; see the bazel output above 1>&2
  exit /B 78
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
echo trace: could not resolve air-trace from %TARGET% ^(got '%RESOLVED%'^) 1>&2
exit /B 78
