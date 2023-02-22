# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
builtin autoload -Uz add-zsh-hook

__intellij_encode() {
  local out=''
  # Use LC_CTYPE=C to process text byte-by-byte and
  # LC_COLLATE=C to compare byte-for-byte. Ensure that
  # LC_ALL and LANG are not set so they don't interfere.
  builtin local i ch hexch LC_CTYPE=C LC_COLLATE=C LC_ALL= LANG=
  builtin local value="$1"
  for ((i = 1; i <= ${#value}; ++i)); do
    ch="$value[i]"
    if [[ "$ch" =~ [/._~A-Za-z0-9-] ]]; then
      out+="$ch"
    else
      builtin printf -v hexch "%02X" "'$ch"
      out+="%$hexch"
    fi
  done
  builtin print -r "$out"
}

__intellij_cmd_preexec() {
  builtin local entered_command=$1
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_started;command=%s;current_directory=%s\a' "$(__intellij_encode "${entered_command}")" "$(__intellij_encode "${current_directory}")"
}

__intellij_command_terminated() {
  builtin local LAST_EXIT_CODE="$?"
  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' "$LAST_EXIT_CODE" "$(__intellij_encode "${current_directory}")"
}

add-zsh-hook preexec __intellij_cmd_preexec
add-zsh-hook precmd __intellij_command_terminated
