:<<"::CMDLITERAL"
@ECHO OFF
GOTO :CMDSCRIPT
::CMDLITERAL

# `treehouse` - the workspace lease tool, the Go binary Bazel builds from `//build/treehouse:treehouse`.
#
# The build-then-run mechanics and the variable contract live in `bazel-go-run.cmd`, beside this file. It builds and
# then runs, rather than using `bazel run`, because `bazel run` holds the workspace lock while the binary it built
# executes. Treehouse needs that more than dev-dist does: an agent acquires a workspace and then runs Bazel inside
# it, so the lock must be free before the binary starts.
#
# The target lives below `community/`, so the runner rewrites the label and the output path in an Ultimate checkout.

set -eu

export BAZEL_GO_NAME="treehouse"
export BAZEL_GO_TARGET="//build/treehouse:treehouse"
export BAZEL_GO_BINARY="build/treehouse/treehouse_/treehouse"
export BAZEL_GO_IN_COMMUNITY="1"

# A second switch that stops the binary from reaching api.github.com for an update check.
export TREEHOUSE_NO_UPDATE_CHECK="1"

script_dir="$(cd "$(dirname "$0")"; pwd)"
exec "$script_dir/bazel-go-run.cmd" "$@"

:CMDSCRIPT

setlocal

REM See the POSIX half above, and bazel-go-run.cmd for the variable contract.

set "BAZEL_GO_NAME=treehouse"
set "BAZEL_GO_TARGET=//build/treehouse:treehouse"
set "BAZEL_GO_BINARY=build/treehouse/treehouse_/treehouse"
set "BAZEL_GO_IN_COMMUNITY=1"

REM A second switch that stops the binary from reaching api.github.com for an update check.
set "TREEHOUSE_NO_UPDATE_CHECK=1"

call "%~dp0bazel-go-run.cmd" %*
set "_exit_code=%ERRORLEVEL%"
EXIT /B %_exit_code%
