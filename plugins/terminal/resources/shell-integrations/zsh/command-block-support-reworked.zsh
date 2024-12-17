# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED:-}" ] && builtin return 0
builtin unset 'INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED'

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
  builtin print -r "$out"
}

# Encodes the string passed as the first parameter to hex.
__jetbrains_intellij_encode() {
  builtin local value="$1"
  if builtin whence od > /dev/null && builtin whence sed > /dev/null && builtin whence tr > /dev/null; then
    builtin printf "%s" "$value" | builtin command od -v -A n -t x1 | builtin command sed 's/ *//g' | builtin command tr -d '\n'
  else
    __jetbrains_intellij_encode_slow "$value"
  fi
}

__jetbrains_intellij_command_preexec() {
  builtin local entered_command="$1"
  builtin printf '\e]1341;command_started;command=%s\a' "$(__jetbrains_intellij_encode "$entered_command")"
}

__jetbrains_intellij_command_precmd() {
  builtin local LAST_EXIT_CODE="$?"
  builtin printf '\e]1341;command_finished;exit_code=%s\a' "$LAST_EXIT_CODE"
}

builtin autoload -Uz add-zsh-hook
add-zsh-hook preexec __jetbrains_intellij_command_preexec
add-zsh-hook precmd __jetbrains_intellij_command_precmd

# This script is sourced from inside a `precmd` hook, i.e. right before the first prompt.
builtin printf '\e]1341;initialized\a'