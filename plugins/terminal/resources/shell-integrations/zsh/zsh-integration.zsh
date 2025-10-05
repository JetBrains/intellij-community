# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# Applies IntelliJ configuration to the current Zsh session:
# 1. Sources `JEDITERM_SOURCE`.
# 2. Updates environment variables using `_INTELLIJ_FORCE_SET_*` and `_INTELLIJ_FORCE_PREPEND_*`.
#
# This function should be called after all startup configuration files (.zshenv,
# .zprofile, .zshrc / .zlogin), so the user session is fully initialized.
# It should also be called before any precmd hooks, so those hooks can see
# the IntelliJ-provided configuration.
#
# For example, the Powerlevel10k precmd hook requires an already activated VirtualEnv
# provided by IntelliJ's Python integration.
function __jetbrains_intellij_configure_session() {
  if [[ -n "${JEDITERM_SOURCE:-}" ]]; then
    if [[ -n "${JEDITERM_SOURCE_SINGLE_ARG}" ]]; then
      # JEDITERM_SOURCE_ARGS might be either list of args or one arg depending on JEDITERM_SOURCE_SINGLE_ARG
      builtin source -- "$JEDITERM_SOURCE" "${JEDITERM_SOURCE_ARGS}"
    else
      builtin source -- "$JEDITERM_SOURCE" ${=JEDITERM_SOURCE_ARGS:-}
    fi
  fi


  # Enable native zsh options to make coding easier.
  builtin emulate -L zsh
  # To use `parameters` associative array
  # https://zsh.sourceforge.io/Doc/Release/Zsh-Modules.html#The-zsh_002fparameter-Module
  builtin zmodload zsh/parameter 2>/dev/null

  builtin local ij_env_name

  # For every _INTELLIJ_FORCE_SET_FOO=BAR run: export FOO=BAR.
  for ij_env_name in ${parameters[(I)_INTELLIJ_FORCE_SET_*]}; do
    # According to "Using associative arrays" https://zsh.sourceforge.io/Guide/zshguide05.html#l122:
    #  use (I) to retrieve all matching keys (not values) with the pattern given.
    builtin local env_name="${ij_env_name:20}"
    builtin export "$env_name"="${(P)ij_env_name}"
    builtin unset "$ij_env_name"
  done
  # For every _INTELLIJ_FORCE_PREPEND_FOO=BAR run: export FOO=BAR$FOO.
  for ij_env_name in ${parameters[(I)_INTELLIJ_FORCE_PREPEND_*]}; do
    builtin local env_name="${ij_env_name:24}"
    builtin export "$env_name"="${(P)ij_env_name}${(P)env_name}"
    builtin unset "$ij_env_name"
  done
}

function __jetbrains_intellij_configure_session_precmd_hook() {
  __jetbrains_intellij_configure_session
  builtin unset -f __jetbrains_intellij_configure_session

  # remove this one-time hook
  builtin typeset -ga precmd_functions
  precmd_functions=(${precmd_functions:#__jetbrains_intellij_configure_session_precmd_hook})
  builtin unset -f __jetbrains_intellij_configure_session_precmd_hook
}

builtin typeset -ga precmd_functions
precmd_functions=(__jetbrains_intellij_configure_session_precmd_hook $precmd_functions)

builtin local command_block_support="${JETBRAINS_INTELLIJ_ZSH_DIR}/command-block-support.zsh"
[ -r "$command_block_support" ] && builtin source "$command_block_support"
builtin local command_block_support_reworked="${JETBRAINS_INTELLIJ_ZSH_DIR}/command-block-support-reworked.zsh"
[ -r "$command_block_support_reworked" ] && builtin source "$command_block_support_reworked"
builtin unset JETBRAINS_INTELLIJ_ZSH_DIR
