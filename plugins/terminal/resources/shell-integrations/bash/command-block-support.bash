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
  builtin local env_vars="$(__jetbrains_intellij_escape_json "$(builtin compgen -A export)")"
  builtin local keyword_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A keyword)")"
  builtin local builtin_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A builtin)")"
  builtin local function_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A function)")"
  builtin local command_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A command)")"
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
  builtin local bash_command="$BASH_COMMAND"
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

  __jetbrains_intellij_report_prompt_state
  if [ -z "$__jetbrains_intellij_initialized" ]; then
    __jetbrains_intellij_initialized='1'
    builtin local shell_info="$(__jetbrains_intellij_collect_shell_info)"
    __jetbrains_intellij_debug_log 'initialized'
    builtin printf '\e]1341;initialized;shell_info=%s\a' "$(__jetbrains_intellij_encode $shell_info)"
    builtin local hist="$(builtin history)"
    builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode "$hist")"
  else
    __jetbrains_intellij_debug_log "command_finished exit_code=$last_exit_code"
    builtin printf '\e]1341;command_finished;exit_code=%s\a' "$last_exit_code"
  fi
}

__jetbrains_intellij_report_prompt_state() {
  builtin local current_directory="$PWD"
  builtin local git_branch=""
  builtin local virtual_env=""
  builtin local conda_env=""
  if builtin command -v git > /dev/null
  then
    git_branch="$(git symbolic-ref --short HEAD 2> /dev/null || git rev-parse --short HEAD 2> /dev/null)"
  fi
  if [[ -n $VIRTUAL_ENV ]]
  then
    virtual_env="$VIRTUAL_ENV"
  fi
  if [[ -n $CONDA_DEFAULT_ENV ]]
  then
    conda_env="$CONDA_DEFAULT_ENV"
  fi
  builtin printf '\e]1341;prompt_state_updated;current_directory=%s;git_branch=%s;virtual_env=%s;conda_env=%s\a' \
    "$(__jetbrains_intellij_encode "${current_directory}")" \
    "$(__jetbrains_intellij_encode "${git_branch}")" \
    "$(__jetbrains_intellij_encode "${virtual_env}")" \
    "$(__jetbrains_intellij_encode "${conda_env}")"
}

__jetbrains_intellij_collect_shell_info() {
  builtin local is_oh_my_bash='false'
  if [ -n "${OSH_THEME:-}" ] || [ -n "${OSH:-}" ] || [ -n "${OSH_CACHE_DIR:-}" ]; then
    is_oh_my_bash='true'
  fi
  builtin local is_starship='false'
  if [ -n "${STARSHIP_START_TIME:-}" ] || [ -n "${STARSHIP_SHELL:-}" ] || [ -n "${STARSHIP_SESSION_KEY:-}" ]; then
    is_starship='true'
  fi
  builtin local is_bash_it='false'
  if [ -n "${BASH_IT_THEME:-}" ] || [ -n "${BASH_IT:-}" ] || [ -n "${BASH_IT_BASHRC:-}" ]; then
    is_bash_it='true'
  fi

  builtin local oh_my_bash_theme="${OSH_THEME:-}"
  builtin local bash_it_theme="${BASH_IT_THEME:-}"
  builtin local oh_my_posh_theme=''
  if [ -n "${POSH_THEME:-}" ] || [ -n "${POSH_PID:-}" ] || [ -n "${POSH_SHELL_VERSION:-}" ]; then
    oh_my_posh_theme="${POSH_THEME:-default}"
  fi

  builtin local content_json="{"\
"\"shellVersion\": \"$(__jetbrains_intellij_escape_json "${BASH_VERSION:-}")\", "\
"\"isOhMyBash\": \"$is_oh_my_bash\", "\
"\"isStarship\": \"$is_starship\", "\
"\"isBashIt\": \"$is_bash_it\", "\
"\"ohMyBashTheme\": \"$(__jetbrains_intellij_escape_json $oh_my_bash_theme)\", "\
"\"ohMyPoshTheme\": \"$(__jetbrains_intellij_escape_json $oh_my_posh_theme)\", "\
"\"bashItTheme\": \"$(__jetbrains_intellij_escape_json $bash_it_theme)\""\
"}"
  builtin printf '%s' $content_json
}

# override clear behaviour to handle it on IDE side and remove the blocks
clear() {
  builtin printf '\e]1341;clear_invoked\a'
}

preexec_functions+=(__jetbrains_intellij_command_started)
precmd_functions+=(__jetbrains_intellij_command_terminated)
HISTIGNORE="${HISTIGNORE-}:__jetbrains_intellij_get_directory_files*:__jetbrains_intellij_get_environment*"
