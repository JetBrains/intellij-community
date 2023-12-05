# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS-}" ] && return

JETBRAINS_INTELLIJ_BASH_DIR="$(dirname "${BASH_SOURCE[0]}")"
if [[ ! -n "${bash_preexec_imported:-}" ]]; then
  # Load bash-preexec if it still not
  # https://github.com/rcaloras/bash-preexec/tree/master#library-authors
  if [[ -r "${JETBRAINS_INTELLIJ_BASH_DIR}/bash-preexec.bash" ]]; then
    source "${JETBRAINS_INTELLIJ_BASH_DIR}/bash-preexec.bash"
  else
    unset JETBRAINS_INTELLIJ_BASH_DIR
    return
  fi
fi

if [ -r "${JETBRAINS_INTELLIJ_BASH_DIR}/bash-fig.bash" ]; then
  source "${JETBRAINS_INTELLIJ_BASH_DIR}/bash-fig.bash"
fi
unset JETBRAINS_INTELLIJ_BASH_DIR

__jetbrains_intellij_encode_slow() {
  local out=''
  # Use LC_CTYPE=C to process text byte-by-byte and
  # LC_COLLATE=C to compare byte-for-byte. Ensure that
  # LC_ALL and LANG are not set so they don't interfere.
  builtin local i hexch LC_CTYPE=C LC_COLLATE=C LC_ALL= LANG=
  builtin local value="$1"
  for ((i = 1; i <= ${#value}; ++i)); do
    builtin printf -v hexch "%02X" "'$value[i]"
    out+="$hexch"
  done
  builtin printf "%s" "$out"
}

__jetbrains_intellij_encode() {
  builtin local value="$1"
  if builtin command -v od > /dev/null && builtin command -v tr > /dev/null; then
    builtin printf "%s" "$value" | od -An -tx1 -v | tr -d "[:space:]"
  else
    __jetbrains_intellij_encode_slow "$value"
  fi
}

__jetbrains_intellij_is_generator_command() {
  [[ "$1" == *"__jetbrains_intellij_get_directory_files"* || "$1" == *"__jetbrains_intellij_get_environment"* ]]
}

__jetbrains_intellij_get_directory_files() {
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin local request_id="$1"
  builtin local result="$(ls -1ap "$2")"
  builtin printf '\e]1341;generator_finished;request_id=%s;result=%s\a' "$request_id" "$(__jetbrains_intellij_encode "${result}")"
}

__jetbrains_intellij_get_environment() {
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin local request_id="$1"
  builtin local env_vars="$(builtin compgen -A export)"
  builtin local keyword_names="$(builtin compgen -A keyword)"
  builtin local builtin_names="$(builtin compgen -A builtin)"
  builtin local function_names="$(builtin compgen -A function)"
  builtin local command_names="$(builtin compgen -A command)"
  builtin local aliases_mapping="$(__jetbrains_intellij_escape_json "$(alias)")"

  builtin local result="{\"envs\": \"$env_vars\", \"keywords\": \"$keyword_names\", \"builtins\": \"$builtin_names\", \"functions\": \"$function_names\", \"commands\": \"$command_names\",  \"aliases\": \"$aliases_mapping\"}"
  builtin printf '\e]1341;generator_finished;request_id=%s;result=%s\a' "$request_id" "$(__jetbrains_intellij_encode "${result}")"
}

__jetbrains_intellij_escape_json() {
  sed -e 's/\\/\\\\/g'\
      -e 's/"/\\"/g'\
      <<< "$1"
}

__jetbrains_intellij_prompt_shown() {
  builtin printf '\e]1341;prompt_shown\a'
}

__jetbrains_intellij_configure_prompt() {
  # Surround 'prompt shown' esc sequence with \[ \] to not count characters inside as part of prompt width
  PS1="\[$(__jetbrains_intellij_prompt_shown)\]"
  # do not show right prompt
  builtin unset RPROMPT
}

__jetbrains_intellij_debug_log() {
  if [ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]; then
    builtin printf "%s\n" "$1"
  fi
}

__jetbrains_intellij_command_started() {
  builtin local bash_command="$1"
  if __jetbrains_intellij_is_generator_command "$bash_command"
  then
    return 0
  fi

  __jetbrains_intellij_clear_all_and_move_cursor_to_top_left
  __jetbrains_intellij_debug_log "command_started '$bash_command'"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' \
     "$(__jetbrains_intellij_encode "$bash_command")" \
     "$(__jetbrains_intellij_encode "$current_directory")"
}

__jetbrains_intellij_clear_all_and_move_cursor_to_top_left() {
  builtin printf '\e[3J\e[1;1H'
}

__jetbrains_intellij_initialized=""

__jetbrains_intellij_command_terminated() {
  builtin local last_exit_code="$?"
  if [ -n "${__JETBRAINS_INTELLIJ_GENERATOR_COMMAND-}" ]
  then
    unset __JETBRAINS_INTELLIJ_GENERATOR_COMMAND
    return 0
  fi

  __jetbrains_intellij_configure_prompt

  builtin local current_directory="$PWD"
  if [ -z "$__jetbrains_intellij_initialized" ]; then
    __jetbrains_intellij_initialized='1'
    __jetbrains_intellij_debug_log 'initialized'
    builtin printf '\e]1341;initialized;current_directory=%s\a' "$(__jetbrains_intellij_encode "$current_directory")"
    builtin local hist="$(builtin history)"
    builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode "$hist")"
  else
    __jetbrains_intellij_debug_log "command_finished exit_code=$last_exit_code"
    builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' "$last_exit_code" \
       "$(__jetbrains_intellij_encode "$current_directory")"
  fi
}

# override clear behaviour to handle it on IDE side and remove the blocks
clear() {
  builtin printf '\e]1341;clear_invoked\a'
}

preexec_functions+=(__jetbrains_intellij_command_started)
precmd_functions+=(__jetbrains_intellij_command_terminated)
HISTIGNORE="${HISTIGNORE-}:__jetbrains_intellij_get_directory_files*:__jetbrains_intellij_get_environment*"
