# The shared bash half of a polyglot `.cmd` wrapper in this directory: `bt.cmd`, `vm.cmd`, `trace.cmd`,
# `bazel-build-run.cmd` and `jscpd.cmd`. Each wrapper starts a binary that Bazel provides, and each file holds a bash
# half and a CMD half. This file holds the five steps the bash halves share. `polyglot-wrapper.cmd` holds the two steps
# the CMD halves share. A wrapper sources this file after its bash pin:
#
#   case "$0" in */*) self_dir="${0%/*}" ;; *) self_dir="." ;; esac
#   . "$self_dir/polyglot-wrapper.sh"
#   polyglot_hand_off_to_cmd "$0" "$@"
#   polyglot_find_roots "$self_dir"
#   polyglot_exec_override bt AIR_BT_BIN 6 "$@"
#   polyglot_build_binary bt "$target" 6 bt
#   exec "$resolved" "$@"
#
# `polyglot_real_path` is the fifth step. `polyglot_build_binary` calls it, and `jscpd.cmd` calls it on its own.
#
# The pin stays in the wrapper. It must run first, and it is the one line that every shell must accept.
# This file sets no shell option. The wrapper owns `set -eu`.

# On Windows a `.cmd` that Git Bash spawns runs its CMD half: MSYS passes the file to `cmd /c`. An explicit
# `bash <wrapper>` runs this bash half instead, and `bazel.cmd` has no bash half for Windows. Hand over to the CMD
# half of the same file, so Windows has one code path. `//c` is the Git Bash spelling of `/c`: MSYS turns one
# leading slash into a path, and leaves a doubled slash alone. `call` keeps the exit code of an `exit /B` inside a
# block, and it keeps a path with a space whole; the implicit `cmd /c` of MSYS loses both. `cygpath -aw` makes a
# relative `$1` an absolute Windows path. The argument conversion stays on, so a `/tmp/x` argument reaches the
# Windows binary as a Windows path. Cygwin is not handled: it converts no path, so `//c` would reach cmd as written.
polyglot_hand_off_to_cmd() {
  case "$(uname -s)" in
    MINGW* | MSYS*) ;;
    *) return 0 ;;
  esac
  local script="$1"
  shift
  exec cmd.exe //c call "$(cygpath -aw "$script")" "$@"
}

# `$2` names the variable that holds an explicit binary. When the variable is set, that binary runs in place of the
# one Bazel provides, and nothing else of the wrapper runs. A value that is not an executable file is a mistake worth
# a message, not a reason to build a different binary. `$3` is the exit code of the wrapper for that mistake.
polyglot_exec_override() {
  local name="$1" variable="$2" code="$3"
  shift 3
  local binary="${!variable:-}"
  [ -n "$binary" ] || return 0
  if [ ! -x "$binary" ]; then
    echo "$name: $variable is not an executable file: $binary" >&2
    exit "$code"
  fi
  exec "$binary" "$@"
}

# The Community root is the parent of `$1`, this directory, in both layouts. Its parent is the checkout root only in
# an Ultimate checkout, where `community/` is this very directory's parent. The last test rules out an unrelated
# `community` directory next to a Community checkout. `label_prefix` is `//` in a Community checkout and
# `@community//` in an Ultimate one, where the community sources are the Bazel module `community`.
polyglot_find_roots() {
  community_root="$(cd -- "$1/.." && pwd)"
  local parent
  parent="$(cd -- "$community_root/.." && pwd)"
  root="$community_root"
  label_prefix="//"
  if [ -f "$parent/MODULE.bazel" ] && [ -f "$parent/bazel.cmd" ] && [ -d "$parent/community" ] &&
     [ "$(cd -- "$parent/community" && pwd)" = "$community_root" ]; then
    root="$parent"
    label_prefix="@community//"
  fi
}

# A path under the output base is a symlink to the real file, and the real file stays valid after
# `bazel clean --expunge`. Where `readlink -f` is unavailable, the unresolved path still works.
polyglot_real_path() {
  readlink -f "$1" 2>/dev/null || printf '%s' "$1"
}

# The local file with the base name `$2` that the build event file `$1` names. The build names its top-level outputs
# in `namedSetOfFiles` records, one JSON record per line, and `--nobuild_event_json_file_path_conversion` makes every
# URI `file://<path>`. An event file that names no such file answers the empty string.
# `polyglot-built-file.ps1` is the same reader for the CMD halves.
polyglot_built_file() {
  awk -v name="$2" '{
    line = $0
    while (match(line, /"uri":"file:\/\/[^"]*"/)) {
      uri = substr(line, RSTART + 14, RLENGTH - 15)
      line = substr(line, RSTART + RLENGTH)
      count = split(uri, parts, "/")
      if (parts[count] == name) found = uri
    }
  } END { print found }' "$1"
}

# Builds `$2`, the Bazel target of the wrapper `$1`, and sets `resolved` to the executable it made, whose base name is
# `$4`. A failed build or a missing executable prints its message and exits with `$3`, the infrastructure code of the
# wrapper. `$5` says where the build output goes: `stderr` (the default) sends it to stderr as it comes, and `log`
# keeps it in a file while stderr is a pipe and shows it only when the build fails. Note 6 of vm.cmd says why.
#
# A `bazel build`, not a `bazel run`. On 2026-10-10 each `bazel run --script_path` of a wrapper label ended with a
# deletion of 430,000 to 487,000 Skyframe nodes, and the next lane build analyzed the whole graph again (7.5 to 23 s).
# The build names its outputs in the build event file, which `polyglot_built_file` reads. `--config=bt`
# (`community/common.bazelrc`) keeps the progress and result lines of Bazel short, and nothing of the build reaches
# stdout, which a wrapper may answer on. Every invocation builds: the binary comes from working-copy sources, and the
# build is the freshness guarantee (note 1 of bt.cmd).
polyglot_build_binary() {
  local name="$1" target="$2" code="$3" basename="$4" output="${5:-stderr}"
  mkdir -p "$root/out/air"
  local events="$root/out/air/$name.events.$$.json"
  local build_log="$root/out/air/$name.build.$$.log"
  local build=(./bazel.cmd build --config=bt "--build_event_json_file=$events"
    --nobuild_event_json_file_path_conversion "$target")
  if [ "$output" = log ] && [ ! -t 2 ]; then
    if ! (cd "$root" && "${build[@]}" >"$build_log" 2>&1); then
      cat "$build_log" >&2
      rm -f "$events" "$build_log"
      echo "$name: could not build $target; see the bazel output above" >&2
      exit "$code"
    fi
    rm -f "$build_log"
  elif ! (cd "$root" && "${build[@]}" >&2); then
    rm -f "$events"
    echo "$name: could not build $target; see the bazel output above" >&2
    exit "$code"
  fi
  resolved="$(polyglot_built_file "$events" "$basename")"
  rm -f "$events"
  resolved="$(polyglot_real_path "$resolved")"
  if [ ! -x "$resolved" ]; then
    echo "$name: could not resolve $basename from $target (got '$resolved')" >&2
    exit "$code"
  fi
}
