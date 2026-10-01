:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# `startup-bench`: the start-up controller over the Bazel dev distribution, running the Rust binary that Bazel builds
# (`community/tools/bt/bins/startup-bench`, one file, always optimized): `@community//tools/bt:startup-bench` in an
# ultimate checkout, `//tools/bt:startup-bench` in a community-only one.
#
# Same shape as `bt.cmd`: no cache, and `bazel run --script_path` on every call, so the binary is always built from
# the working copy. `AIR_STARTUP_BENCH_BIN` names a binary and skips the build. An infrastructure failure of the
# wrapper exits with 6, the `INFRA` code of the binary.

# A .cmd file has no shebang, so pin the interpreter, as bt.cmd does.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

# Resolve the Community root in either layout, then use its parent only for an Ultimate checkout. `root` reaches the
# binary as BUILD_WORKSPACE_DIRECTORY, the root of every path the binary builds.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
community_root="$(cd -- "$self_dir/.." && pwd)"
parent="$(cd -- "$community_root/.." && pwd)"
root="$community_root"
target="//tools/bt:startup-bench"
if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
   [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
  root="$parent"
  target="@community//tools/bt:startup-bench"
fi

export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

if [ -n "${AIR_STARTUP_BENCH_BIN:-}" ]; then
  if [ ! -x "$AIR_STARTUP_BENCH_BIN" ]; then
    echo "startup-bench: AIR_STARTUP_BENCH_BIN is not an executable file: $AIR_STARTUP_BENCH_BIN" >&2
    exit 6
  fi
  exec "$AIR_STARTUP_BENCH_BIN" "$@"
fi

mkdir -p "$root/out/startup-bench"
launcher="$root/out/startup-bench/startup-bench.launcher.$$"
# `--config=bt` keeps the progress lines of Bazel quiet, and `>&2` keeps the rest off stdout, where a `--json` run
# writes one envelope.
if ! (cd "$root" && ./bazel.cmd run --config=bt --script_path="$launcher" "$target" >&2); then
  rm -f "$launcher"
  echo "startup-bench: could not build $target; see the bazel output above" >&2
  exit 6
fi

# `bazel run --script_path` writes `<binary> <args> "$@"` as the last line. Only its first word is the binary.
resolved="$(awk 'END { print $1 }' "$launcher")"
rm -f "$launcher"
resolved="$(readlink -f "$resolved" 2>/dev/null || printf '%s' "$resolved")"
if [ ! -x "$resolved" ]; then
  echo "startup-bench: could not resolve the binary from $target (got '$resolved')" >&2
  exit 6
fi

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM A session runs on macOS only. The Windows half builds and runs the binary, so `replay` and the refusals work.

for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "PARENT=%%~fd"
for %%d in ("%PARENT%\community") do set "PARENT_COMMUNITY=%%~fd"
set "ROOT=%COMMUNITY_ROOT%"
set "TARGET=//tools/bt:startup-bench"
if not exist "%PARENT%\MODULE.bazel" goto :ROOTREADY
if not exist "%PARENT%\bazel.cmd" goto :ROOTREADY
if not exist "%PARENT_COMMUNITY%\" goto :ROOTREADY
if /I not "%PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :ROOTREADY
set "ROOT=%PARENT%"
set "TARGET=@community//tools/bt:startup-bench"
:ROOTREADY

set "BUILD_WORKSPACE_DIRECTORY=%ROOT%"
set "JAVA_RUNFILES="
set "RUNFILES_DIR="
set "RUNFILES_MANIFEST_FILE="
set "RUNFILES_MANIFEST_ONLY="
set "TEST_SRCDIR="

REM `shift` does not rewrite %*, so the arguments are collected one at a time.
set "ARGS="
:PARSEARGS
if "%~1"=="" goto :PARSED
set "ARGS=%ARGS% %1"
shift
goto :PARSEARGS
:PARSED

if defined AIR_STARTUP_BENCH_BIN (
  if not exist "%AIR_STARTUP_BENCH_BIN%" (
    echo startup-bench: AIR_STARTUP_BENCH_BIN is not an executable file: %AIR_STARTUP_BENCH_BIN% 1>&2
    exit /B 6
  )
  "%AIR_STARTUP_BENCH_BIN%"%ARGS%
  exit /B %ERRORLEVEL%
)

if not exist "%ROOT%\out\startup-bench" mkdir "%ROOT%\out\startup-bench"
set "LAUNCHER=%ROOT%\out\startup-bench\startup-bench.launcher.%RANDOM%"
pushd "%ROOT%"
call bazel.cmd run --config=bt --script_path="%LAUNCHER%" "%TARGET%" 1>&2
set "STATUS=%ERRORLEVEL%"
popd
if not "%STATUS%"=="0" (
  if exist "%LAUNCHER%" del /q "%LAUNCHER%"
  echo startup-bench: could not build %TARGET%; see the bazel output above 1>&2
  exit /B 6
)

REM Only the last line is wanted: `<binary> <args> %*`.
set "RESOLVED="
for /f "usebackq tokens=1" %%b in ("%LAUNCHER%") do set "RESOLVED=%%~b"
del /q "%LAUNCHER%"
if not defined RESOLVED goto :RESOLVEFAILED
if not exist "%RESOLVED%" goto :RESOLVEFAILED

"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%

:RESOLVEFAILED
echo startup-bench: could not resolve the binary from %TARGET% ^(got '%RESOLVED%'^) 1>&2
exit /B 6
