# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

[ -z "${INTELLIJ_TERMINAL_COMMAND_BLOCKS_REWORKED-}" ] && return

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

# Should be executed before command start
__jetbrains_intellij_command_preexec() {
  __jetbrains_intellij_command_running="1"
  __jetbrains_intellij_should_update_prompt="1"

  builtin local entered_command="${BASH_COMMAND:-}"
  builtin printf '\e]1341;command_started;command=%s\a' "$(__jetbrains_intellij_encode "$entered_command")"

  # Restore the original prompt, our integration will be injected back after command execution in `__jetbrains_intellij_update_prompt`.
  PS1="$__jetbrains_intellij_original_ps1"
}

# Should be executed before printing of the prompt (for example, after command execution)
__jetbrains_intellij_command_precmd() {
  builtin local LAST_EXIT_CODE="$?"

  if [[ -z "$__jetbrains_intellij_initialized" ]]; then
    __jetbrains_intellij_install_debug_trap
    __jetbrains_intellij_initialized="1"
    builtin printf '\e]1341;initialized\a'
  elif [[ -n "$__jetbrains_intellij_command_running" ]]; then
    builtin local current_directory="$PWD"
    builtin printf '\e]1341;command_finished;exit_code=%s;current_directory=%s\a' \
      "$LAST_EXIT_CODE" \
      "$(__jetbrains_intellij_encode "$current_directory")"
  fi

  if [ -n "$__jetbrains_intellij_should_update_prompt" ]; then
    __jetbrains_intellij_update_prompt
  fi

  __jetbrains_intellij_should_update_prompt=""
  __jetbrains_intellij_command_running=""
}

__jetbrains_intellij_update_prompt() {
  if [[ "$__jetbrains_intellij_custom_ps1" == "" || "$__jetbrains_intellij_custom_ps1" != "$PS1" ]]; then
    # Save the original prompt
    __jetbrains_intellij_original_ps1="$PS1"
    __jetbrains_intellij_custom_ps1="\[$(__jetbrains_intellij_prompt_started)\]$PS1\[$(__jetbrains_intellij_prompt_finished)\]"
    PS1="$__jetbrains_intellij_custom_ps1"
  fi
}

__jetbrains_intellij_prompt_started() {
  builtin printf '\e]1341;prompt_started\a'
}

__jetbrains_intellij_prompt_finished() {
  builtin printf '\e]1341;prompt_finished\a'
}

__jetbrains_intellij_install_debug_trap() {
  __jetbrains_intellij_original_debug_trap="$(__jetbrains_intellij_get_debug_trap)"
  trap '__jetbrains_intellij_debug_trap "$_"' DEBUG
}

# Our debug trap is wrapping the original one.
# We execute our preexec function if conditions are met,
# but always execute the original trap after that (if any).
__jetbrains_intellij_debug_trap() {
  if __jetbrains_intellij_is_prompt_command_contains "${BASH_COMMAND:-}"; then
    # We are executing something inside the PROMPT_COMMAND.
    # It is not the user command, so we need to skip it.
    # But we need to ensure that prompt will be updated.
    # It is important in the case of Ctrl+C in the prompt when there is no running command:
    # in the next precmd we need to update the prompt without sending the command started event.
    __jetbrains_intellij_should_update_prompt="1"
    __jetbrains_intellij_run_original_debug_trap
    return
  fi

  # This function is executed in a DEBUG trap, so it can be called multiple times when command is started.
  # But we need to handle only first call.
  if [[ -n "$__jetbrains_intellij_command_running" ]]; then
    __jetbrains_intellij_run_original_debug_trap
    return
  fi

  # Execute our preexec function before the original one.
  __jetbrains_intellij_command_preexec

  __jetbrains_intellij_run_original_debug_trap
}

__jetbrains_intellij_run_original_debug_trap() {
  if [[ -n "$__jetbrains_intellij_original_debug_trap" && "$__jetbrains_intellij_original_debug_trap" != "-" ]]; then
    builtin eval "${__jetbrains_intellij_original_debug_trap}"
  fi
}

# Returns the code that should be executed in the DEBUG trap.
# 'trap -p DEBUG' outputs a shell command in the format `trap -- '<shell_code>' DEBUG`.
# The shell code can contain quotes, spaces and line breaks.
# To get the shell code, we need to parse this string into an array and then get the 2nd item.
# We have to use `eval` to preserve quoting.
__jetbrains_intellij_get_debug_trap() {
	builtin local -a values
	builtin eval "values=($(trap -p "DEBUG"))"
	builtin printf '%s' "${values[2]:-}"
}

__jetbrains_intellij_trim_whitespaces() {
  builtin local text="$1"
  text="${text#"${text%%[![:space:]]*}"}"   # Remove leading whitespace characters
  text="${text%"${text##*[![:space:]]}"}"   # Remove trailing whitespace characters
  builtin printf '%s' "$text"
}

# Checks if first argument value contains in the PROMPT_COMMAND variable.
__jetbrains_intellij_is_prompt_command_contains() {
  builtin local IFS=$'\n;'
  builtin local prompt_command_array
  builtin read -rd '' -a prompt_command_array <<< "${PROMPT_COMMAND[*]:-}"

  builtin local text_to_find="$(__jetbrains_intellij_trim_whitespaces "$1")"

  builtin local command
  for command in "${prompt_command_array[@]:-}"; do
    command="$(__jetbrains_intellij_trim_whitespaces "$command")"
    if [[ "$command" == "$text_to_find" ]]; then
      return 0
    fi
  done

  return 1
}

# Inspired by https://unix.stackexchange.com/questions/460651/is-prompt-command-a-colon-separated-list/672843#672843
__jetbrains_intellij_append_to_prompt_command() {
    local separator=$'\n'
    if [[ ${#PROMPT_COMMAND[@]} -gt 1 ]]; then
    	PROMPT_COMMAND+=("$1")
    else
    	PROMPT_COMMAND="${PROMPT_COMMAND:+$PROMPT_COMMAND${separator}}${1}"
    fi
}

__jetbrains_intellij_original_ps1=""
__jetbrains_intellij_custom_ps1=""

__jetbrains_intellij_command_running="1"
__jetbrains_intellij_should_update_prompt="1"

__jetbrains_intellij_append_to_prompt_command "__jetbrains_intellij_command_precmd"