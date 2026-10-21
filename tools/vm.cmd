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
#    `bazel build`, and the build's event file names the binary: the build *is* the freshness guarantee. Neither
#    half runs a `bazel run`, because on 2026-10-10 each `bazel run` of this label ended with a deletion of
#    about 487,000 Skyframe nodes, and the next lane build analyzed the whole graph again (7.5 to 23 s).
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
# 5. **No `--config` for the guest on the build.** The controller's lane builds pass no build option of their own
#    (`host_bazel_argv` in `vm/crates/avl-vm/src/lane/bazel.rs`), because Bazel keys its analysis cache on build
#    options. So this wrapper, the lane build and every other command share one analysis. The guest is a target of the
#    lane, not an option (`plugins/air/tests/integration/ui_lane_ide.bzl` of the ultimate root).
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
#    collected one at a time, and the binary from the build event file, which `polyglot-built-file.ps1` reads
#    as a Windows path. It has no `readlink -f`, because the event file of a Windows build names the binary
#    itself.
#
# Everything else is bt.cmd's, for bt.cmd's reasons: the bash pinning, the shared build step of
# polyglot-wrapper.sh, the `readlink -f` fallback, and exporting BUILD_WORKSPACE_DIRECTORY while unsetting any
# leaked RUNFILES_*.

# A .cmd file has no shebang, so an ENOEXEC fallback picks the interpreter - dash on Ubuntu, bash on
# macOS. Pin it, as tests.cmd and bt.cmd do, rather than leaving the wrapper's behaviour to the caller.
[ -z "$BASH_VERSION" ] && exec /bin/bash "$0" "$@"

set -eu

case "$0" in
  */*) self_dir="${0%/*}" ;;
  *) self_dir="." ;;
esac
# The shared steps of the polyglot wrappers: the Git Bash hand-off, the override, the roots, the real path.
. "$self_dir/polyglot-wrapper.sh"
polyglot_hand_off_to_cmd "$0" "$@"

# In a Community checkout the controller refuses with `air_area_missing`, because no `bt.json` there names the Air area.
polyglot_find_roots "$self_dir"
target="${label_prefix}tools/vm:vm"

# BUILD_WORKSPACE_DIRECTORY is how the controller finds `bt.json` and `community/tools/vm/provision` - a Bazel-built
# binary's own path is inside a Bazel output tree and says nothing about the checkout. Any RUNFILES_* in
# the environment leaked from an outer invocation and would only mislead a runfiles lookup.
export BUILD_WORKSPACE_DIRECTORY="$root"
unset JAVA_RUNFILES RUNFILES_DIR RUNFILES_MANIFEST_FILE RUNFILES_MANIFEST_ONLY TEST_SRCDIR

# The override is checked before Bazel is consulted at all: naming a binary that is not there is a
# mistake worth reporting, not a reason to quietly build a different one.
polyglot_exec_override vm AIR_VM_BIN 78 "$@"

# `log`: for a pipe, the build output goes to a log file and reaches stderr only when the build fails; a terminal
# sees it (note 6 above). A failure here is infrastructure, which this wrapper reports as 78 - the one code no
# controller command uses (note 4 above). The controller reads the same kind of event file for its own lane build,
# so this is the one way to ask Bazel where its output went.
polyglot_build_binary vm "$target" 78 vm log

exec "$resolved" "$@"

:CMDSCRIPT

setlocal

REM The same steps as the sh half, see note 7. The `build` step of polyglot-wrapper.cmd runs the `bazel build` and
REM reads the build event file through PowerShell.

call "%~dp0polyglot-wrapper.cmd"
set "TARGET=%LABEL_PREFIX%tools/vm:vm"

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

REM The pipe path of note 6 (`log`): the build output reaches stderr only when the build fails. A failure exits 78,
REM for the reason of note 4.
call "%POLYGLOT_WRAPPER%" build vm "%TARGET%" 78 vm.exe log
if not "%ERRORLEVEL%"=="0" exit /B %ERRORLEVEL%

"%RESOLVED%"%ARGS%
exit /B %ERRORLEVEL%
