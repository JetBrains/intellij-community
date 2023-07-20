# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
builtin autoload -Uz add-zsh-hook

__intellij_encode() {
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

__intellij_encode_large() {
  builtin local value="$1"
  if builtin whence od > /dev/null && builtin whence sed > /dev/null && builtin whence tr > /dev/null; then
    builtin echo -n "$value" | od -v -A n -t x1 | sed 's/ *//g' | tr -d '\n'
  else
    __intellij_encode "$value"
  fi
}

__intellij_prompt_shown() {
  builtin printf '\e]1341;prompt_shown\a'
}

__intellij_configure_prompt() {
  PS1="%{$(__intellij_prompt_shown)%}"
  # do not show right prompt
  builtin unset RPS1
  builtin unset RPROMPT
  # always show new prompt after completion list
  builtin unsetopt ALWAYS_LAST_PROMPT
}

__intellij_command_preexec() {
  builtin local entered_command="$1"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' "$(__intellij_encode "${entered_command}")" "$(__intellij_encode "${current_directory}")"
}

__intellij_command_precmd() {
  builtin local LAST_EXIT_CODE="$?"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' "$LAST_EXIT_CODE" "$(__intellij_encode "${current_directory}")"
  __intellij_configure_prompt
}

add-zsh-hook preexec __intellij_command_preexec
add-zsh-hook precmd __intellij_command_precmd

# Do not show "zsh: do you wish to see all <N> possibilities (<M> lines)?" question
# when there are big number of completion items
LISTMAX=1000000

# This script is sourced from inside a `precmd` hook, i.e. right before the first prompt.
builtin printf '\e]1341;initialized\a'

__intellij_configure_prompt

# `HISTFILE` is already initialized at this point.
# Get all commands from history from the first command
builtin local hist="$(builtin history 1)"
builtin printf '\e]1341;command_history;history_string=%s\a' "$(__intellij_encode_large "${hist}")"