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
    builtin printf "%s" "$value" | od -v -A n -t x1 | sed 's/ *//g' | tr -d '\n'
  else
    __jetbrains_intellij_encode "$value"
  fi
}

__jetbrains_intellij_is_generator_command() {
  [[ "$1" == *"__jetbrains_intellij_get_directory_files"* || "$1" == *"__jetbrains_intellij_get_environment"* ]]
}

__jetbrains_intellij_get_directory_files() {
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin local request_id="$1"
  builtin local result="$(ls -1ap "$2")"
  builtin printf '\e]1341;generator_finished;request_id=%s;result=%s\a' "$request_id" "$(__jetbrains_intellij_encode_large "${result}")"
}

__jetbrains_intellij_get_environment() {
  __JETBRAINS_INTELLIJ_GENERATOR_COMMAND=1
  builtin local request_id="$1"
  builtin local env_vars="$(builtin print -l -- ${(ko)parameters[(R)*export*]})"
  builtin local keyword_names="$(builtin print -l -- ${(ko)reswords})"
  builtin local builtin_names="$(builtin print -l -- ${(ko)builtins})"
  builtin local function_names="$(builtin print -l -- ${(ko)functions})"
  builtin local command_names="$(builtin print -l -- ${(ko)commands})"
  builtin local aliases_mapping="$(__jetbrains_intellij_escape_json "$(alias)")"

  builtin local result="{\"envs\": \"$env_vars\", \"keywords\": \"$keyword_names\", \"builtins\": \"$builtin_names\", \"functions\": \"$function_names\", \"commands\": \"$command_names\", \"aliases\": \"$aliases_mapping\"}"
  builtin printf '\e]1341;generator_finished;request_id=%s;result=%s\a' "$request_id" "$(__jetbrains_intellij_encode_large "${result}")"
}

__jetbrains_intellij_escape_json() {
  sed -e 's/\\/\\\\/g'\
      -e 's/"/\\"/g'\
      <<< "$1"
}

__jetbrains_intellij_zshaddhistory() {
	! __jetbrains_intellij_is_generator_command "$1"
}

__jetbrains_intellij_prompt_shown() {
  builtin printf '\e]1341;prompt_shown\a'
}

__jetbrains_intellij_configure_prompt() {
  PS1="%{$(__jetbrains_intellij_prompt_shown)%}"
  # do not show right prompt
  builtin unset RPS1
  builtin unset RPROMPT
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
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' \
    "$LAST_EXIT_CODE" "$(__jetbrains_intellij_encode "${current_directory}")"
  builtin print "${JETBRAINS_INTELLIJ_COMMAND_END_MARKER:-}"
  __jetbrains_intellij_configure_prompt
}

# override clear behaviour to handle it on IDE side and remove the blocks
clear() {
  builtin printf '\e]1341;clear_invoked\a'
}

add-zsh-hook preexec __jetbrains_intellij_command_preexec
add-zsh-hook precmd __jetbrains_intellij_command_precmd
add-zsh-hook zshaddhistory __jetbrains_intellij_zshaddhistory

__jetbrains_intellij_configure_prompt

# `HISTFILE` is already initialized at this point.
# Get all commands from history from the first command
builtin local hist="$(builtin history 1)"
builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode_large "${hist}")"

# This script is sourced from inside a `precmd` hook, i.e. right before the first prompt.
builtin printf '\e]1341;initialized;current_directory=%s\a' "$(__jetbrains_intellij_encode "$PWD")"
builtin print "${JETBRAINS_INTELLIJ_COMMAND_END_MARKER:-}"
