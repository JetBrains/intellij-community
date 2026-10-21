:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Shared runner: build one Bazel target, then run it.
#
# A caller is a thin `.cmd` that sets the variables below and then hands control to this file.
# `build/dev-dist.cmd` is the caller. The split copies `bun.cmd` plus `tool-wrapper.*`: the per-tool file holds only
# the facts, and one implementation holds the mechanics.
#
# Built and then run, rather than `bazel run`: the caller runs Bazel itself, and `bazel run` holds the
# workspace lock while the binary it built executes, so the nested build would wait for its own parent.
#
# Variable contract, set by the caller:
#
#   BAZEL_RUN_TARGET        Required. The Bazel label, with no repository prefix. Example:
#                          `//build/dev-dist-tools/bins/dev-dist:dev-dist_opt`. This file adds `@community` when it is
#                          needed.
#   BAZEL_RUN_BINARY        Required. The path of the built binary below `out/bazel-bin`. Use `/` separators and no
#                          file extension. Example: `build/dev-dist-tools/bins/dev-dist/dev-dist_opt/dev-dist`. The
#                          Windows half swaps the separators and appends `.exe`, so one value serves both halves.
#   BAZEL_RUN_IN_COMMUNITY  Optional. Set it to `1` when the target lives below `community/`. Both the label and the
#                          output path change in an Ultimate checkout. See "Two checkout layouts" below.
#   BAZEL_RUN_NAME          Optional. The name this file prints in a diagnostic. Default: `bazel-build-run`.
#
# This file clears all four variables before it starts the binary, so a nested call to another caller reads its own
# values only.
#
# Two checkout layouts. Both roots hold `MODULE.bazel` and `bazel.cmd`, so those two files do not tell them apart.
# The distinguisher is the `community/` subdirectory, which only the Ultimate root has. In an Ultimate checkout the
# community sources are the Bazel module `community`, added by a `local_path_override` in the root `MODULE.bazel`:
#
#   layout     BAZEL_RUN_IN_COMMUNITY  label                  binary below out/bazel-bin
#   Community  1                      //build/x:x            build/x/x_/x
#   Ultimate   1                      @community//build/x:x  external/community+/build/x/x_/x
#   Ultimate   unset                  //build/x:x            build/x/x_/x
#
# `community+` is Bazel's own mangling of the module name, not a name this repository states. When a Bazel upgrade
# changes the mangling, the build still passes and the binary check below fails. That is why the message names the
# path and the prefix.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter - dash on Ubuntu, bash on macOS. Pin it,
# as bt.cmd and tests.cmd do. The pin stays above `set -e`, because a false `[` test ends an `&&` list non-zero.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

name="${BAZEL_RUN_NAME:-bazel-build-run}"

if [ -z "${BAZEL_RUN_TARGET:-}" ]; then
  echo "$name: BAZEL_RUN_TARGET is not set" >&2
  exit 1
fi
if [ -z "${BAZEL_RUN_BINARY:-}" ]; then
  echo "$name: BAZEL_RUN_BINARY is not set" >&2
  exit 1
fi

# `${0%/*}` yields `$0` unchanged when this file is invoked with no slash in the path, so the `case` restores what
# `dirname` would have given.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
# The shared steps of the polyglot wrappers: the Git Bash hand-off and the roots. A caller exports the variables
# above, so the CMD half reads the same contract after the hand-off.
. "$self_dir/polyglot-wrapper.sh"
polyglot_hand_off_to_cmd "$0" "$@"
polyglot_find_roots "$self_dir"

label="$BAZEL_RUN_TARGET"
binary="$BAZEL_RUN_BINARY"
if [ "$label_prefix" = "@community//" ] && [ "${BAZEL_RUN_IN_COMMUNITY:-}" = "1" ]; then
  label="@community$BAZEL_RUN_TARGET"
  binary="external/community+/$BAZEL_RUN_BINARY"
fi

cd -- "$root"

# Bazel's own output goes to stderr, so a caller that writes JSON on stdout keeps a clean stdout. `set +e` around
# the build is what keeps the real exit code: inside `if ! cmd`, `$?` is already the status of the `!`.
set +e
./bazel.cmd build "$label" >&2
status=$?
set -e
if [ "$status" -ne 0 ]; then
  echo "$name: could not build $label; see the Bazel output above" >&2
  exit "$status"
fi

binary_path="$root/out/bazel-bin/$binary"
if [ ! -x "$binary_path" ]; then
  echo "$name: the build of $label succeeded, but $binary_path is not an executable file" >&2
  echo "$name: check BAZEL_RUN_BINARY, and the 'external/community+' prefix this file assumes" >&2
  exit 1
fi

unset BAZEL_RUN_NAME BAZEL_RUN_TARGET BAZEL_RUN_BINARY BAZEL_RUN_IN_COMMUNITY
exec "$binary_path" "$@"

:CMDSCRIPT

setlocal

REM Same design as the POSIX half above, which holds the variable contract and the two checkout layouts.

if "%BAZEL_RUN_NAME%"=="" set "BAZEL_RUN_NAME=bazel-build-run"

if "%BAZEL_RUN_TARGET%"=="" (
  echo %BAZEL_RUN_NAME%: BAZEL_RUN_TARGET is not set 1>&2
  exit /B 1
)
if "%BAZEL_RUN_BINARY%"=="" (
  echo %BAZEL_RUN_NAME%: BAZEL_RUN_BINARY is not set 1>&2
  exit /B 1
)

call "%~dp0polyglot-wrapper.cmd"
set "BIN_PREFIX="
set "LABEL=%BAZEL_RUN_TARGET%"
if "%LABEL_PREFIX%"=="//" goto :ROOTREADY
if not "%BAZEL_RUN_IN_COMMUNITY%"=="1" goto :ROOTREADY
set "LABEL=@community%BAZEL_RUN_TARGET%"
set "BIN_PREFIX=external\community+\"
:ROOTREADY

REM One caller variable serves both halves: swap the separators and append the Windows extension.
set "BINARY=%ROOT%\out\bazel-bin\%BIN_PREFIX%%BAZEL_RUN_BINARY:/=\%.exe"

REM `shift` does not rewrite %*, so the forwarded arguments are collected one at a time.
set "ARGS="
:PARSEARGS
if "%~1"=="" goto :PARSED
set "ARGS=%ARGS% %1"
shift
goto :PARSEARGS
:PARSED

pushd "%ROOT%"
REM `<nul` keeps Bazel off the console stdin, which the binary below may need.
call "bazel.cmd" build "%LABEL%" <nul
set "_exit_code=%ERRORLEVEL%"
if not "%_exit_code%"=="0" goto :BUILDFAILED
if not exist "%BINARY%" goto :NOBINARY

set "BAZEL_RUN_NAME="
set "BAZEL_RUN_TARGET="
set "BAZEL_RUN_BINARY="
set "BAZEL_RUN_IN_COMMUNITY="
call "%BINARY%"%ARGS%
set "_exit_code=%ERRORLEVEL%"
popd
EXIT /B %_exit_code%

:BUILDFAILED
popd
echo %BAZEL_RUN_NAME%: could not build %LABEL%; see the Bazel output above 1>&2
EXIT /B %_exit_code%

:NOBINARY
popd
echo %BAZEL_RUN_NAME%: the build of %LABEL% succeeded, but "%BINARY%" is missing 1>&2
echo %BAZEL_RUN_NAME%: check BAZEL_RUN_BINARY, and the 'external\community+' prefix this file assumes 1>&2
EXIT /B 1
