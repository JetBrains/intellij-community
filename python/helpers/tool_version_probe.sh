#!/bin/sh

# Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
# Some fields are deliberately split below; never interpret their contents as path globs.
set -f
# Keep character classes and output from standard utilities deterministic.
LC_ALL=C
export LC_ALL

# POSIX command substitution removes trailing newlines. Keep one literal newline for
# separating shell startup output from the final command -v result.
NL='
'

is_number() {
  case "$1" in
    ''|*[!0-9]*) return 1 ;;
    *) return 0 ;;
  esac
}

json_string() {
  printf '"'
  # JSON encoders cannot be assumed to exist on a target. The extra newline makes
  # awk process an empty value and preserves newlines already present at the end.
  # A character loop avoids non-portable gsub replacement escaping in BSD/GNU awk.
  printf '%s\n' "$1" | awk '
    function escape(value, result, i, char) {
      for (i = 1; i <= length(value); i++) {
        char = substr(value, i, 1)
        if (char == "\\") result = result "\\\\"
        else if (char == "\"") result = result "\\\""
        else if (char == "\t") result = result "\\t"
        else if (char == "\r") result = result "\\r"
        # Other control bytes cannot be encoded portably with POSIX awk.
        else if (char !~ /[[:cntrl:]]/) result = result char
      }
      return result
    }

    NR > 1 { printf "\\n" }
    { printf "%s", escape($0) }
  '
  printf '"'
}

shell_single_quote() {
  # Produce one shell word that is safe to interpolate into TARGET_SHELL -c.
  # An embedded quote is represented by closing the quotes, escaping it, and reopening them.
  value=$1
  printf "'"
  while [ -n "$value" ]; do
    char=${value%"${value#?}"}
    value=${value#?}
    if [ "$char" = "'" ]; then
      printf "'\\\\''"
    else
      printf '%s' "$char"
    fi
  done
  printf "'"
}

parse_passwd() {
  # passwd fields are colon-separated; home and shell are fields 6 and 7.
  old_ifs=$IFS
  IFS=:
  set -- $1
  IFS=$old_ifs
  PASSWD_HOME=$6
  PASSWD_SHELL=$7
}

env_value() {
  # eval provides POSIX-compatible indirect expansion after the variable name is validated.
  case "$1" in
    ''|*[!abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_]*) return 0 ;;
  esac
  eval "printf '%s' \"\${$1-}\""
}

append_path_component() {
  base=$1
  component=$2
  # A missing env/home base invalidates the path instead of turning it into a root-relative one.
  if [ -z "$base" ]; then
    return 0
  fi
  printf '%s/%s' "${base%/}" "$component"
}

read_components_path() {
  # Consume a counted list of path components from argv and append it to the base.
  base=$1
  component_count=$2
  shift 2

  is_number "$component_count" || exit 2
  i=0
  result=$base
  while [ "$i" -lt "$component_count" ]; do
    [ "$#" -gt 0 ] || exit 2
    result=$(append_path_component "$result" "$1")
    shift
    i=$((i + 1))
  done
  printf '%s' "$result"
}

run_version_output() {
  tool_path=$1
  output_file=${TMPDIR:-/tmp}/ij_tool_probe_$$.out
  # Noclobber and a private umask avoid following or exposing a pre-created temporary file.
  (umask 077; set -C; : > "$output_file") 2>/dev/null || return 1
  trap 'rm -f "$output_file"' 0 1 2 15

  # Avoid a pipeline so the --version exit status remains available.
  "$tool_path" --version > "$output_file" 2>/dev/null
  version_status=$?
  if [ "$version_status" -eq 0 ]; then
    # Bound output
    dd bs=1024 count=1 < "$output_file" 2>/dev/null
  fi
  rm -f "$output_file"
  trap - 0 1 2 15
  return "$version_status"
}

run_python_probe() {
  python_path=$1
  [ -f "$python_path" ] && [ -x "$python_path" ] || return 1

  free_threaded=$("$python_path" -c '
from __future__ import print_function
import sys

is_gil_enabled = getattr(sys, "_is_gil_enabled", None)
print("true" if callable(is_gil_enabled) and not is_gil_enabled() else "false")
' 2>/dev/null) || return 1
  case "$free_threaded" in
    true|false) ;;
    *) return 1 ;;
  esac

  version_output=$("$python_path" --version 2>&1) || return 1
  printf '{"isExecutable":true,"freeThreaded":%s,"versionOutput":' "$free_threaded"
  json_string "$version_output"
  printf '}'
}

# argv protocol:
#   --python <path-or-empty>
#   repeated <tool-name> <search-path-count> <search-path>...
# A search path is one of:
#   absolute <directory>
#   env <variable> <component-count> <component>...
#   home <component-count> <component>...
# stdout is one JSON object containing shell, home, python, and found tools. Exit 2 means malformed argv.
# python is null when not requested, minimal isExecutable=false on failure, and a full object on success.
[ "$#" -ge 2 ] || exit 2
[ "$1" = "--python" ] || exit 2
PYTHON_PATH=$2
shift 2

USER_NAME=$(whoami 2>/dev/null || true)
PASSWD_HOME=
PASSWD_SHELL=
# Environment values are preferred below; passwd data provides a fallback on targets
# whose process environment does not expose HOME or SHELL.
if [ -n "$USER_NAME" ]; then
  PASSWD_LINE=$(getent passwd "$USER_NAME" 2>/dev/null || true)
  parse_passwd "$PASSWD_LINE"
fi

TARGET_HOME=${HOME:-$PASSWD_HOME}
TARGET_SHELL=${SHELL:-$PASSWD_SHELL}
[ -n "$TARGET_SHELL" ] || TARGET_SHELL=/bin/sh

printf '{"shell":'
json_string "$TARGET_SHELL"
printf ',"home":'
json_string "$TARGET_HOME"
printf ',"python":'
if [ -z "$PYTHON_PATH" ]; then
  printf 'null'
elif python_probe=$(run_python_probe "$PYTHON_PATH"); then
  printf '%s' "$python_probe"
else
  printf '{"isExecutable":false}'
fi
printf ',"tools":{'

first_tool=1
while [ "$#" -gt 0 ]; do
  [ "$#" -ge 2 ] || exit 2

  tool_name=$1
  search_path_count=$2
  shift 2
  is_number "$search_path_count" || exit 2

  tool_path=
  if [ -n "$tool_name" ]; then
    # Run lookup in the target user's shell so its PATH initialization applies. Some
    # shell startup files print text, therefore only the final output line is considered.
    quoted_tool_name=$(shell_single_quote "$tool_name")
    detected_tool_path=$("$TARGET_SHELL" -l -c "command -v $quoted_tool_name" 2>/dev/null || true)
    detected_tool_path=${detected_tool_path##*"$NL"}
    if [ -f "$detected_tool_path" ] && [ -x "$detected_tool_path" ]; then
      tool_path=$detected_tool_path
    fi
  fi

  i=0
  while [ "$i" -lt "$search_path_count" ]; do
    [ "$#" -gt 0 ] || exit 2
    search_path_kind=$1
    shift

    case "$search_path_kind" in
      absolute)
        [ "$#" -gt 0 ] || exit 2
        search_dir=$1
        shift
        ;;
      env)
        [ "$#" -ge 2 ] || exit 2
        prefix_env_var=$1
        component_count=$2
        shift 2
        search_dir=$(read_components_path "$(env_value "$prefix_env_var")" "$component_count" "$@") || exit 2
        shift "$component_count" || exit 2
        ;;
      home)
        [ "$#" -gt 0 ] || exit 2
        component_count=$1
        shift
        search_dir=$(read_components_path "$TARGET_HOME" "$component_count" "$@") || exit 2
        shift "$component_count" || exit 2
        ;;
      *)
        exit 2
        ;;
    esac

    if [ -z "$tool_path" ] && [ -n "$search_dir" ]; then
      candidate="${search_dir%/}/$tool_name"
      if [ -f "$candidate" ] && [ -x "$candidate" ]; then
        tool_path=$candidate
      fi
    fi

    i=$((i + 1))
  done

  if [ -n "$tool_path" ]; then
    # A valid executable is still reported when --version fails; null distinguishes that case.
    version_output=
    version_output_present=0
    if version_output=$(run_version_output "$tool_path"); then
      version_output_present=1
    fi
    if [ "$first_tool" -eq 0 ]; then
      printf ','
    fi
    first_tool=0
    json_string "$tool_name"
    printf ':{"path":'
    json_string "$tool_path"
    printf ',"versionOutput":'
    if [ "$version_output_present" -eq 1 ]; then
      json_string "$version_output"
    else
      printf 'null'
    fi
    printf '}'
  fi
done

printf '}}\n'
