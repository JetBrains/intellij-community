:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# Shared runner: build one Bazel Go target, then run it.
#
# A caller is a thin `.cmd` that sets the variables below and then hands control to this file.
# `community/tools/treehouse.cmd` and `build/dev-dist.cmd` are the two callers. The split copies `bun.cmd` plus
# `tool-wrapper.*`: the per-tool file holds only the facts, and one implementation holds the mechanics.
#
# Built and then run, rather than `bazel run`: these commands run Bazel themselves, and `bazel run` holds the
# workspace lock while the binary it built executes, so the nested build would wait for its own parent. Treehouse
# needs this more sharply than dev-dist. An agent acquires a workspace and then runs Bazel inside it, so the lock
# must be free before the binary starts.
#
# Variable contract, set by the caller:
#
#   BAZEL_GO_TARGET        Required. The Bazel label, with no repository prefix. Example:
#                          `//build/treehouse:treehouse`. This file adds `@community` when it is needed.
#   BAZEL_GO_BINARY        Required. The path of the built binary below `out/bazel-bin`. Use `/` separators and no
#                          file extension. Example: `build/treehouse/treehouse_/treehouse`. The Windows half swaps
#                          the separators and appends `.exe`, so one value serves both halves.
#   BAZEL_GO_IN_COMMUNITY  Optional. Set it to `1` when the target lives below `community/`. Both the label and the
#                          output path change in an Ultimate checkout. See "Two checkout layouts" below.
#   BAZEL_GO_NAME          Optional. The name this file prints in a diagnostic. Default: `bazel-go-run`.
#
# This file clears all four variables before it starts the binary, so a nested call to another caller reads its own
# values only.
#
# Two checkout layouts. Both roots hold `MODULE.bazel` and `bazel.cmd`, so those two files do not tell them apart.
# The distinguisher is the `community/` subdirectory, which only the Ultimate root has. In an Ultimate checkout the
# community sources are the Bazel module `community`, added by a `local_path_override` in the root `MODULE.bazel`:
#
#   layout     BAZEL_GO_IN_COMMUNITY  label                  binary below out/bazel-bin
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

name="${BAZEL_GO_NAME:-bazel-go-run}"

if [ -z "${BAZEL_GO_TARGET:-}" ]; then
  echo "$name: BAZEL_GO_TARGET is not set" >&2
  exit 1
fi
if [ -z "${BAZEL_GO_BINARY:-}" ]; then
  echo "$name: BAZEL_GO_BINARY is not set" >&2
  exit 1
fi

# `${0%/*}` yields `$0` unchanged when this file is invoked with no slash in the path, so the `case` restores what
# `dirname` would have given.
case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
# The parent of this file's directory is the community root in both layouts: `<ultimate>/community/tools/..` and
# `<community>/tools/..`.
community_root="$(cd -- "$self_dir/.." && pwd)"
parent="$(cd -- "$community_root/.." && pwd)"

root="$community_root"
label="$BAZEL_GO_TARGET"
binary="$BAZEL_GO_BINARY"

# The last test rules out a parent that holds an unrelated directory named `community`.
if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
   [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
  root="$parent"
  if [ "${BAZEL_GO_IN_COMMUNITY:-}" = "1" ]; then
    label="@community$BAZEL_GO_TARGET"
    binary="external/community+/$BAZEL_GO_BINARY"
  fi
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
  echo "$name: check BAZEL_GO_BINARY, and the 'external/community+' prefix this file assumes" >&2
  exit 1
fi

unset BAZEL_GO_NAME BAZEL_GO_TARGET BAZEL_GO_BINARY BAZEL_GO_IN_COMMUNITY
exec "$binary_path" "$@"

:CMDSCRIPT

setlocal

REM Same design as the POSIX half above, which holds the variable contract and the two checkout layouts.

if "%BAZEL_GO_NAME%"=="" set "BAZEL_GO_NAME=bazel-go-run"

if "%BAZEL_GO_TARGET%"=="" (
  echo %BAZEL_GO_NAME%: BAZEL_GO_TARGET is not set 1>&2
  exit /B 1
)
if "%BAZEL_GO_BINARY%"=="" (
  echo %BAZEL_GO_NAME%: BAZEL_GO_BINARY is not set 1>&2
  exit /B 1
)

REM `%~dp0` ends with a backslash, so `%~dp0..` is the parent of this file's directory. `%%~fd` resolves the `..`,
REM which is what makes the identity test below comparable.
for %%d in ("%~dp0..") do set "COMMUNITY_ROOT=%%~fd"
for %%d in ("%COMMUNITY_ROOT%\..") do set "PARENT=%%~fd"
for %%d in ("%PARENT%\community") do set "PARENT_COMMUNITY=%%~fd"

set "ROOT=%COMMUNITY_ROOT%"
set "BIN_PREFIX="
set "LABEL=%BAZEL_GO_TARGET%"

REM A `goto` chain rather than a nested `if (...)` block: plain expansion reads a stale value inside a block.
if not exist "%PARENT%\MODULE.bazel" goto :ROOTREADY
if not exist "%PARENT%\bazel.cmd" goto :ROOTREADY
if not exist "%PARENT_COMMUNITY%\" goto :ROOTREADY
if /I not "%PARENT_COMMUNITY%"=="%COMMUNITY_ROOT%" goto :ROOTREADY
set "ROOT=%PARENT%"
if not "%BAZEL_GO_IN_COMMUNITY%"=="1" goto :ROOTREADY
set "LABEL=@community%BAZEL_GO_TARGET%"
set "BIN_PREFIX=external\community+\"
:ROOTREADY

REM One caller variable serves both halves: swap the separators and append the Windows extension.
set "BINARY=%ROOT%\out\bazel-bin\%BIN_PREFIX%%BAZEL_GO_BINARY:/=\%.exe"

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

set "BAZEL_GO_NAME="
set "BAZEL_GO_TARGET="
set "BAZEL_GO_BINARY="
set "BAZEL_GO_IN_COMMUNITY="
call "%BINARY%"%ARGS%
set "_exit_code=%ERRORLEVEL%"
popd
EXIT /B %_exit_code%

:BUILDFAILED
popd
echo %BAZEL_GO_NAME%: could not build %LABEL%; see the Bazel output above 1>&2
EXIT /B %_exit_code%

:NOBINARY
popd
echo %BAZEL_GO_NAME%: the build of %LABEL% succeeded, but "%BINARY%" is missing 1>&2
echo %BAZEL_GO_NAME%: check BAZEL_GO_BINARY, and the 'external\community+' prefix this file assumes 1>&2
EXIT /B 1
