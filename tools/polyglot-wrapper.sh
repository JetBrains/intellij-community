# The shared bash half of a polyglot `.cmd` wrapper in this directory: `bt.cmd`, `vm.cmd`, `trace.cmd`,
# `bazel-build-run.cmd` and `jscpd.cmd`. Each wrapper starts a binary that Bazel provides, and each file holds a bash
# half and a CMD half. This file holds the four steps the bash halves share. `polyglot-wrapper.cmd` holds the one step
# the CMD halves share. A wrapper sources this file after its bash pin:
#
#   case "$0" in */*) self_dir="${0%/*}" ;; *) self_dir="." ;; esac
#   . "$self_dir/polyglot-wrapper.sh"
#   polyglot_hand_off_to_cmd "$0" "$@"
#   polyglot_exec_override bt AIR_BT_BIN 6 "$@"
#   polyglot_find_roots "$self_dir"
#   resolved="$(polyglot_real_path "$resolved")"
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
