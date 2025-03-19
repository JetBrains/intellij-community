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

# Util method. Serializes string so that it could be safely passed to the escape sequence payload.
__jetbrains_intellij_encode() {
  builtin local value="$1"
  if builtin command -v od > /dev/null && builtin command -v tr > /dev/null; then
    builtin printf "%s" "$value" | builtin command od -An -tx1 -v | builtin command tr -d "[:space:]"
  else
    __jetbrains_intellij_encode_slow "$value"
  fi
}

__jetbrains_intellij_is_generator_command() {
  [[ "$1" == *"__jetbrains_intellij_run_generator"*  || "$1" == *"__jetbrains_intellij_report_shell_editor_buffer"* ]]
}

__jetbrains_intellij_run_generator() {
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin local request_id="$1"
  builtin local command="$2"
  # Can't be joined with an assignment, otherwise we will fail to capture the exit code of eval.
  builtin local result
  result="$(eval "$command" 2>&1)"
  builtin local exit_code=$?
  builtin printf '\e]1341;generator_finished;request_id=%s;result=%s;exit_code=%s\a' "$request_id" \
    "$(__jetbrains_intellij_encode "$result")" \
    "$exit_code"
}

__jetbrains_intellij_get_directory_files() {
  command ls -1ap "$1"
}

__jetbrains_intellij_get_aliases() {
  __jetbrains_intellij_escape_json "$(alias)"
}

__jetbrains_intellij_get_environment() {
  builtin local env_vars="$(__jetbrains_intellij_escape_json "$(builtin compgen -A export)")"
  builtin local keyword_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A keyword)")"
  builtin local builtin_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A builtin)")"
  builtin local function_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A function)")"
  builtin local command_names="$(__jetbrains_intellij_escape_json "$(builtin compgen -A command)")"
  builtin local aliases_mapping="$(__jetbrains_intellij_get_aliases)"

  builtin local result="{\"envs\": \"$env_vars\", \"keywords\": \"$keyword_names\", \"builtins\": \"$builtin_names\", \"functions\": \"$function_names\", \"commands\": \"$command_names\",  \"aliases\": \"$aliases_mapping\"}"
  builtin printf '%s' "$result"
}

__jetbrains_intellij_escape_json() {
  builtin command sed -e 's/\\/\\\\/g'\
      -e 's/"/\\"/g'\
      <<< "$1"
}

# Store our PS1 value in a variable to reference it in a convenient way
# Surround 'prompt shown' esc sequence with \[ \] to not count characters inside as part of prompt width
__JETBRAINS_INTELLIJ_PS1='\[\e]1341;prompt_shown\a\]'

__jetbrains_intellij_configure_prompt() {
  if [ "$PS1" != $__JETBRAINS_INTELLIJ_PS1 ]; then
    # Remember the original prompt to use it in '__jetbrains_intellij_report_prompt_state'
    __JETBRAINS_INTELLIJ_ORIGINAL_PS1=$PS1
  fi
  # Trick: We put escape sequence to the PS1 so that every time prompt is shown, the event is triggered for IJ, but it stays invisible for end-user.
  PS1=$__JETBRAINS_INTELLIJ_PS1
}

__jetbrains_intellij_debug_log() {
  if [ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]; then
    builtin printf "%s\n" "$1"
  fi
}

__jetbrains_intellij_command_started() {
  # The real command, typed by user.
  builtin local typed_command="$1"
  # Resolved command to be really executed by Bash. (i.e. alias value)
  builtin local bash_command="$BASH_COMMAND"
  if __jetbrains_intellij_is_generator_command "$bash_command"
  then
    return 0
  fi

  __jetbrains_intellij_clear_all_and_move_cursor_to_top_left
  __jetbrains_intellij_debug_log "command_started '$bash_command'"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' \
     "$(__jetbrains_intellij_encode "$typed_command")" \
     "$(__jetbrains_intellij_encode "$current_directory")"
}

__jetbrains_intellij_clear_all_and_move_cursor_to_top_left() {
  builtin printf '\e[3J\e[1;1H'
}

__jetbrains_intellij_initialized=""

__jetbrains_intellij_command_terminated() {
  builtin local last_exit_code="$?"
  __jetbrains_intellij_configure_prompt
  if [ -n "${__JETBRAINS_INTELLIJ_GENERATOR_COMMAND-}" ]
  then
    unset __JETBRAINS_INTELLIJ_GENERATOR_COMMAND
    return 0
  fi

  if [ -z "$__jetbrains_intellij_initialized" ]; then
    __jetbrains_intellij_initialized='1'
    __jetbrains_intellij_fix_prompt_command_order
    builtin local hist="$(HISTTIMEFORMAT="" builtin history)"
    builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode "$hist")"
    builtin local shell_info="$(__jetbrains_intellij_collect_shell_info)"
    __jetbrains_intellij_debug_log 'initialized'
    builtin printf '\e]1341;initialized;shell_info=%s\a' "$(__jetbrains_intellij_encode $shell_info)"
  else
    __jetbrains_intellij_debug_log "command_finished exit_code=$last_exit_code"
    builtin printf '\e]1341;command_finished;exit_code=%s\a' "$last_exit_code"
  fi
  __jetbrains_intellij_report_prompt_state
}

__jetbrains_intellij_report_prompt_state() {
  builtin local current_directory="$PWD"
  builtin local user_name="${USER:-}"
  builtin local user_home="${HOME:-}"
  builtin local git_branch=""
  builtin local virtual_env=""
  builtin local conda_env=""
  if builtin command -v git > /dev/null
  then
    git_branch="$(builtin command git symbolic-ref --short HEAD 2> /dev/null || builtin command git rev-parse --short HEAD 2> /dev/null)"
  fi
  if [[ -n $VIRTUAL_ENV ]]
  then
    virtual_env="$VIRTUAL_ENV"
  fi
  if [[ -n $CONDA_DEFAULT_ENV ]]
  then
    conda_env="$CONDA_DEFAULT_ENV"
  fi

  builtin local prompt="$__JETBRAINS_INTELLIJ_ORIGINAL_PS1"
  builtin local expanded_prompt=""
  # Prompt expansion was introduced in 4.4 version of Bash
  if [[ -n "${BASH_VERSINFO-}" ]] && (( BASH_VERSINFO[0] > 4 || (BASH_VERSINFO[0] == 4 && BASH_VERSINFO[1] >= 4) ))
  then
    expanded_prompt=${prompt@P}
  else
    # Launch a subshell with a desired prompt, then parse the output
    expanded_prompt=$(PS1="$prompt" "$BASH" --norc -i </dev/null 2>&1 | sed -n '${s/^\(.*\)exit$/\1/p;}')
  fi

  builtin printf '\e]1341;prompt_state_updated;current_directory=%s;user_name=%s;user_home=%s;git_branch=%s;virtual_env=%s;conda_env=%s;original_prompt=%s;original_right_prompt=%s\a' \
    "$(__jetbrains_intellij_encode "${current_directory}")" \
    "$(__jetbrains_intellij_encode "${user_name}")" \
    "$(__jetbrains_intellij_encode "${user_home}")" \
    "$(__jetbrains_intellij_encode "${git_branch}")" \
    "$(__jetbrains_intellij_encode "${virtual_env}")" \
    "$(__jetbrains_intellij_encode "${conda_env}")" \
    "$(__jetbrains_intellij_encode "${expanded_prompt}")" \
    "" # there is no dedicated variable for right prompt in Bash, so send an empty string
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

# Bash-preexec lib is modifying the PROMPT_COMMAND variable in order to call precmd_functions.
# But it is placing '__bp_precmd_invoke_cmd' function to the start of the PROMPT_COMMAND,
# so all other hooks from the plugins (like PS1 updating) are invoked after our precmd_functions.
# And at the moment of our '__jetbrains_intellij_command_terminated' call, we see an outdated PS1.
# This function is reordering the functions in the PROMPT_COMMAND placing the Bash-preexec hooks to the end.
function __jetbrains_intellij_fix_prompt_command_order() {
  function cleanup_command() {
    builtin local command="$1"
    command="${command//__bp_precmd_invoke_cmd/}"
    command="${command//__bp_interactive_mode/}"
    command="${command//$'\n':$'\n'/$'\n'}"
    # it is the function from the Bash-preexec
    __bp_sanitize_string command "$command"
    if [[ "${command:-:}" == ":" ]]; then
          command=
    fi
    printf '%s' "$command"
  }

  __jetbrains_intellij_debug_log "Before PROMPT_COMMAND modification: $(declare -p PROMPT_COMMAND)"
  # PROMPT_COMMAND is an array in Bash >= 5.1, so we need two implementations
  if [[ -n "${BASH_VERSINFO-}" ]] && (( BASH_VERSINFO[0] > 5 || (BASH_VERSINFO[0] == 5 && BASH_VERSINFO[1] >= 1) )); then
    # Remove the bash-preexec functions from the PROMPT_COMMAND array
    for index in "${!PROMPT_COMMAND[@]}"; do
      builtin local cur_command="${PROMPT_COMMAND[$index]}"
      cur_command="$(cleanup_command "$cur_command")"
      if [[ -n $cur_command ]]; then
        PROMPT_COMMAND[$index]=$cur_command
      else
        unset 'PROMPT_COMMAND[$index]'
      fi
    done
    # Add removed functions to the end of the array
    PROMPT_COMMAND+=('__bp_precmd_invoke_cmd')
    PROMPT_COMMAND+=('__bp_interactive_mode')
    # Fix the gaps in the array because of removed items
    builtin local new_array
    for i in "${!PROMPT_COMMAND[@]}"; do
      new_array+=( "${PROMPT_COMMAND[i]}" )
    done
    PROMPT_COMMAND=("${new_array[@]}")
  else
    PROMPT_COMMAND="$(cleanup_command "$PROMPT_COMMAND")"
    PROMPT_COMMAND+=$'\n__bp_precmd_invoke_cmd\n__bp_interactive_mode'
  fi

  unset -f cleanup_command
  __jetbrains_intellij_debug_log "After PROMPT_COMMAND modification: $(declare -p PROMPT_COMMAND)"
}

# Avoid conflict with user defined alias
unalias clear 2>/dev/null
# Override clear behaviour to handle it on IDE side and remove the blocks
function clear() {
  builtin printf '\e]1341;clear_invoked\a'
}

# This function will be triggered by a key bindings.
function __jetbrains_intellij_report_shell_editor_buffer () {
  # The commands executed by `bind -x` also trigger `PREEXEC` and `PRECMD` (unlike ZSH' `bindkey`).
  # Mark as generator to avoid triggering `command_started` and `command_finished` events.
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin printf '\e]1341;shell_editor_buffer_reported;shell_editor_buffer=%s\a' "$(__jetbrains_intellij_encode "${READLINE_LINE:-}")"
}
# Remove binding if exists.
builtin bind -r '"\eo"'
# Bind [Esc, o] key sequence to report prompt buffer.
builtin bind -x '"\eo":"__jetbrains_intellij_report_shell_editor_buffer"'

preexec_functions+=(__jetbrains_intellij_command_started)
precmd_functions+=(__jetbrains_intellij_command_terminated)
HISTIGNORE="${HISTIGNORE-}:__jetbrains_intellij_run_generator*"
HISTIGNORE="${HISTIGNORE-}:__jetbrains_intellij_report_shell_editor_buffer*"
