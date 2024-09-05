# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS:-}" ] && builtin return 0
builtin unset 'INTELLIJ_TERMINAL_COMMAND_BLOCKS'

builtin autoload -Uz add-zsh-hook

__jetbrains_intellij_encode() {
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
  builtin print -r "$out"
}

__jetbrains_intellij_encode_large() {
  builtin local value="$1"
  if builtin whence od > /dev/null && builtin whence sed > /dev/null && builtin whence tr > /dev/null; then
    builtin printf "%s" "$value" | builtin command od -v -A n -t x1 | builtin command sed 's/ *//g' | builtin command tr -d '\n'
  else
    __jetbrains_intellij_encode "$value"
  fi
}

__jetbrains_intellij_is_generator_command() {
  [[ "$1" == *"__jetbrains_intellij_run_generator"* ]]
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
    "$(__jetbrains_intellij_encode_large "$result")" \
    "$exit_code"
}

__jetbrains_intellij_get_directory_files() {
  command ls -1ap "$1"
}

__jetbrains_intellij_get_aliases() {
  __jetbrains_intellij_escape_json "$(alias)"
}

__jetbrains_intellij_get_environment() {
  builtin local env_vars="$(__jetbrains_intellij_escape_json "$(builtin print -l -- ${(ko)parameters[(R)*export*]})")"
  builtin local keyword_names="$(__jetbrains_intellij_escape_json "$(builtin print -l -- ${(ko)reswords})")"
  builtin local builtin_names="$(__jetbrains_intellij_escape_json "$(builtin print -l -- ${(ko)builtins})")"
  builtin local function_names="$(__jetbrains_intellij_escape_json "$(builtin print -l -- ${(ko)functions})")"
  builtin local command_names="$(__jetbrains_intellij_escape_json "$(builtin print -l -- ${(ko)commands})")"
  builtin local aliases_mapping="$(__jetbrains_intellij_get_aliases)"

  builtin local result="{\"envs\": \"$env_vars\", \"keywords\": \"$keyword_names\", \"builtins\": \"$builtin_names\", \"functions\": \"$function_names\", \"commands\": \"$command_names\", \"aliases\": \"$aliases_mapping\"}"
  builtin printf '%s' "$result"
}

__jetbrains_intellij_escape_json() {
  builtin command sed -e 's/\\/\\\\/g'\
      -e 's/"/\\"/g'\
      <<< "$1"
}

__jetbrains_intellij_zshaddhistory() {
	! __jetbrains_intellij_is_generator_command "$1"
}

__jetbrains_intellij_command_preexec() {
  if __jetbrains_intellij_is_generator_command "$1"
  then
    return 0
  fi
  __jetbrains_intellij_clear_all_and_move_cursor_to_top_left
  builtin local entered_command="$1"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' \
    "$(__jetbrains_intellij_encode "${entered_command}")" \
    "$(__jetbrains_intellij_encode "${current_directory}")"
}

__jetbrains_intellij_clear_all_and_move_cursor_to_top_left() {
  builtin printf '\e[3J\e[1;1H'
}

__jetbrains_intellij_command_precmd() {
  builtin local LAST_EXIT_CODE="$?"
  if [ ! -z $__JETBRAINS_INTELLIJ_GENERATOR_COMMAND ]
  then
    unset __JETBRAINS_INTELLIJ_GENERATOR_COMMAND
    return 0
  fi
  builtin printf '\e]1341;command_finished;exit_code=%s\a' "$LAST_EXIT_CODE"
  builtin print "${JETBRAINS_INTELLIJ_COMMAND_END_MARKER:-}"
  __jetbrains_intellij_report_prompt_state
}

__jetbrains_intellij_report_prompt_state() {
  builtin local current_directory="$PWD"
  builtin local user_name="${USER:-}"
  builtin local user_home="${HOME:-}"
  builtin local git_branch=""
  builtin local virtual_env=""
  builtin local conda_env=""
  if builtin whence git > /dev/null
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
  builtin local original_prompt="$(builtin print -rP $PS1 2>/dev/null)"
  builtin local original_right_prompt="$(builtin print -rP $RPROMPT 2>/dev/null)"
  builtin printf '\e]1341;prompt_state_updated;current_directory=%s;user_name=%s;user_home=%s;git_branch=%s;virtual_env=%s;conda_env=%s;original_prompt=%s;original_right_prompt=%s\a' \
    "$(__jetbrains_intellij_encode "${current_directory}")" \
    "$(__jetbrains_intellij_encode "${user_name}")" \
    "$(__jetbrains_intellij_encode "${user_home}")" \
    "$(__jetbrains_intellij_encode "${git_branch}")" \
    "$(__jetbrains_intellij_encode "${virtual_env}")" \
    "$(__jetbrains_intellij_encode "${conda_env}")" \
    "$(__jetbrains_intellij_encode "${original_prompt}")" \
    "$(__jetbrains_intellij_encode "${original_right_prompt}")"
}

__jetbrains_intellij_collect_shell_info() {
  builtin local is_oh_my_zsh='false'
  if [ -n "${ZSH_THEME:-}" ] || [ -n "${ZSH_COMPDUMP:-}" ] || [ -n "${ZSH_CACHE_DIR:-}" ]; then
    is_oh_my_zsh='true'
  fi
  builtin local is_p10k='false'
  if [ -n "${P9K_VERSION:-}" ]; then
    is_p10k='true'
  fi
  builtin local is_starship='false'
  if [ -n "${STARSHIP_START_TIME:-}" ] || [ -n "${STARSHIP_SHELL:-}" ] || [ -n "${STARSHIP_SESSION_KEY:-}" ]; then
    is_starship='true'
  fi
  builtin local is_spaceship='false'
  if [ -n "${SPACESHIP_CONFIG_PATH:-}" ] || [ -n "${SPACESHIP_PROMPT_ORDER:-}" ]; then
    is_spaceship='true'
  fi
  builtin local is_prezto='false'
  if [ -n "${ZPREZTODIR:-}" ]; then
    is_prezto='true'
  fi

  builtin local oh_my_zsh_theme="${ZSH_THEME:-}"
  builtin local oh_my_posh_theme=''
  if [ -n "${POSH_THEME:-}" ] || [ -n "${POSH_PID:-}" ] || [ -n "${POSH_SHELL_VERSION:-}" ]; then
    oh_my_posh_theme="${POSH_THEME:-default}"
  fi
  builtin local prezto_theme=''
  zstyle -s ':prezto:module:prompt' theme prezto_theme

  builtin local content_json="{"\
"\"shellVersion\": \"$(__jetbrains_intellij_escape_json "${ZSH_VERSION:-}")\", "\
"\"isOhMyZsh\": \"$is_oh_my_zsh\", "\
"\"isP10K\": \"$is_p10k\", "\
"\"isStarship\": \"$is_starship\", "\
"\"isSpaceship\": \"$is_spaceship\", "\
"\"isPrezto\": \"$is_prezto\", "\
"\"ohMyZshTheme\": \"$(__jetbrains_intellij_escape_json $oh_my_zsh_theme)\", "\
"\"ohMyPoshTheme\": \"$(__jetbrains_intellij_escape_json $oh_my_posh_theme)\", "\
"\"preztoTheme\": \"$(__jetbrains_intellij_escape_json $prezto_theme)\""\
"}"
  builtin printf '%s' $content_json
}

# Avoid conflict with user defined alias
unalias clear 2>/dev/null
# Override clear behaviour to handle it on IDE side and remove the blocks
function clear() {
  builtin printf '\e]1341;clear_invoked\a'
}

# This function will be triggered by a key bindings as ZLE widget.
function __jetbrains_intellij_report_shell_editor_buffer () {
  builtin printf '\e]1341;shell_editor_buffer_reported;shell_editor_buffer=%s\a' "$(__jetbrains_intellij_encode "${BUFFER:-}")"
}
# `bindkey` is a part of ZLE, therefore all ZLE widgets need to be registered with `zle -N`
# See https://zsh.sourceforge.io/Doc/Release/Zsh-Line-Editor.html#Zle-Widgets
zle -N __jetbrains_intellij_report_shell_editor_buffer
# Remove binding if exists.
builtin bindkey -r '\eo'
# Bind [Esc, o] key sequence to report prompt buffer.
builtin bindkey '\eo' __jetbrains_intellij_report_shell_editor_buffer

add-zsh-hook preexec __jetbrains_intellij_command_preexec
add-zsh-hook precmd __jetbrains_intellij_command_precmd
add-zsh-hook zshaddhistory __jetbrains_intellij_zshaddhistory

# `HISTFILE` is already initialized at this point.
# Get all commands from history from the first command
builtin local hist="$(builtin history 1)"
builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode_large "${hist}")"

builtin local shell_info="$(__jetbrains_intellij_collect_shell_info)"
# This script is sourced from inside a `precmd` hook, i.e. right before the first prompt.
builtin printf '\e]1341;initialized;shell_info=%s\a' "$(__jetbrains_intellij_encode_large $shell_info)"
builtin print "${JETBRAINS_INTELLIJ_COMMAND_END_MARKER:-}"

__jetbrains_intellij_report_prompt_state
