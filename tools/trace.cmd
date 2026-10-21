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
#    `bazel build` and the build is the freshness guarantee. A stale planner would print commands
#    for a lane layout that no longer exists.
# 2. **`AIR_TRACE_BIN` overrides everything on the sh half, checked first.** It names an explicit binary and
#    skips Bazel, for a debugger attached to a hand-built binary. The CMD half reads it the same way (note 5).
# 3. **Wrapper failures exit 78** (`EX_CONFIG`), a code `air-trace` itself never returns, so "the tool did not
#    start" stays distinguishable from anything the tool answers.
# 4. **No `--config` for the guest on the build**, as in vm.cmd. A call between `vm.cmd daemon warm` and
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

case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
# The shared steps of the polyglot wrappers: the Git Bash hand-off, the override, the roots, the real path.
. "$self_dir/polyglot-wrapper.sh"
polyglot_hand_off_to_cmd "$0" "$@"

# In a Community checkout `plan` refuses with `air_area_missing`, because no `bt.json` there names the Air area.
polyglot_find_roots "$self_dir"
target="${label_prefix}tools/vm:air-trace"

export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

polyglot_exec_override trace AIR_TRACE_BIN 78 "$@"

# The build output goes to stderr as it comes, which keeps stdout for the answer of `plan`.
polyglot_build_binary trace "$target" 78 air-trace

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM The same steps as the sh half, see note 5. The `build` step of polyglot-wrapper.cmd runs the `bazel build` and
REM reads the build event file through PowerShell.

call "%~dp0polyglot-wrapper.cmd"
set "TARGET=%LABEL_PREFIX%tools/vm:air-trace"

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

REM The build output goes to stderr, which keeps stdout for the answer of `plan`. A failure exits 78 (note 3).
call "%POLYGLOT_WRAPPER%" build trace "%TARGET%" 78 air-trace.exe
if not "%ERRORLEVEL%"=="0" exit /B %ERRORLEVEL%

"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%
