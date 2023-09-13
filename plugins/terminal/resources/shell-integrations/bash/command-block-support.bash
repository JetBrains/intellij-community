# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS-}" ] && return

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
  builtin printf "$out"
}

__jetbrains_intellij_encode() {
  builtin local value="$1"
  if builtin command -v od > /dev/null && builtin command -v tr > /dev/null; then
    builtin printf "$value" | od -An -tx1 -v | tr -d "[:space:]"
  else
    __jetbrains_intellij_encode_slow "$value"
  fi
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
    builtin printf "$1\n"
  fi
}

__jetbrains_intellij_command_started() {
  builtin local bash_command="$BASH_COMMAND"
  if [[ "$bash_command" != "$PROMPT_COMMAND" ]]; then
    __jetbrains_intellij_debug_log "command_started '$bash_command'"
    builtin local current_directory="$PWD"
    builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' \
       "$(__jetbrains_intellij_encode "$bash_command")" \
       "$(__jetbrains_intellij_encode "$current_directory")"
  fi
}

__jetbrains_intellij_initialized=""

__jetbrains_intellij_command_terminated() {
  builtin local last_exit_code="$?"

  # Show completions on first TAB if there are more than one suitable option
  # (by default Bash show all options only after second TAB in a such case)
  builtin bind 'set show-all-if-ambiguous on'
  # Do not show "Display all N possibilities?" question during completion
  builtin bind 'set completion-query-items 0'
  # Print all completion items at once instead of pagination
  builtin bind 'set page-completions off'

  __jetbrains_intellij_configure_prompt

  if [ -z "$__jetbrains_intellij_initialized" ]; then
    __jetbrains_intellij_initialized='1'
    __jetbrains_intellij_debug_log 'initialized'
    builtin printf '\e]1341;initialized\a'
    builtin local hist="$(builtin history)"
    builtin printf '\e]1341;command_history;history_string=%s\a' "$(__jetbrains_intellij_encode "$hist")"
    trap '__jetbrains_intellij_command_started' DEBUG
  else
    builtin local current_directory="$PWD"
    __jetbrains_intellij_debug_log "command_finished exit_code=$last_exit_code"
    builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' "$last_exit_code" \
       "$(__jetbrains_intellij_encode "$current_directory")"
  fi
}

PROMPT_COMMAND='__jetbrains_intellij_command_terminated'
