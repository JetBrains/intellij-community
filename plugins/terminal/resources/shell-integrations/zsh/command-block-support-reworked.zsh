# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED:-}" ] && builtin return 0
builtin unset 'INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED'

# Do not source our shell integration when PowerLevel10K plugin is available
# because it will work incorrectly until IJPL-178955 is fixed.
[ -n "${P9K_VERSION:-}" ] && builtin return 0

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

  __jetbrains_intellij_command_running="1"
  # Restore the original prompt, our integration will be injected back after command execution in `__jetbrains_intellij_update_prompt`.
  PS1="$__jetbrains_intellij_original_ps1"
}

__jetbrains_intellij_command_precmd() {
  # Should be always the first line in the function!
  builtin local LAST_EXIT_CODE="$?"

  if [[ -z "${__jetbrains_intellij_initialized-}" ]]; then
    # As `precmd` is executed before each prompt, for the first time it is called after
    # all rc files have been processed and before the first prompt is displayed.
    # So, here it finishes the initialization block, not a user command.
    __jetbrains_intellij_initialized=1
    builtin printf '\e]1341;initialized\a'
    __jetbrains_intellij_update_prompt
    builtin return
  fi

  builtin local current_directory="$PWD"
  builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' \
    "$LAST_EXIT_CODE" \
    "$(__jetbrains_intellij_encode "$current_directory")"

  if [ -n "$__jetbrains_intellij_command_running" ]; then
    __jetbrains_intellij_update_prompt
    __jetbrains_intellij_command_running=""
  fi
}

__jetbrains_intellij_update_prompt() {
  # Save the original prompt
  __jetbrains_intellij_original_ps1="$PS1"
  PS1="%{$(__jetbrains_intellij_prompt_started)%}$PS1%{$(__jetbrains_intellij_prompt_finished)%}"
}

__jetbrains_intellij_prompt_started() {
  builtin printf '\e]1341;prompt_started\a'
}

__jetbrains_intellij_prompt_finished() {
  builtin printf '\e]1341;prompt_finished\a'
}

builtin autoload -Uz add-zsh-hook
add-zsh-hook preexec __jetbrains_intellij_command_preexec
add-zsh-hook precmd __jetbrains_intellij_command_precmd
