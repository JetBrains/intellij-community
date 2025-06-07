# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# See doc in .zshenv for how IntelliJ injects itself into Zsh startup process.

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: before loading ${(%):-%x}"
fi

# HISTFILE is set to `${ZDOTDIR:-$HOME}/.zsh_history` in /etc/zshrc when ZDOTDIR
# pointed to an internal IntelliJ directory, so the HISTFILE variable is set incorrectly.
# Correct it before sourcing the original .zshrc as user configuration may depend on it.
HISTFILE="${JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR:-$HOME}/.zsh_history"

JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE='.zshrc'
builtin source "$JETBRAINS_INTELLIJ_ZSH_DIR/zdotdir/source-original.zsh"

# Restore original ZDOTDIR. Once ZDOTDIR is restored, further user configuration files are
# sourced normally by Zsh. At this point, only .zlogin remains, so it's read directly by Zsh.
if [[ -n "${JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR-}" ]]; then
  ZDOTDIR="$JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR"
  builtin unset 'JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR'
else
  # defaults ZDOTDIR to HOME
  builtin unset 'ZDOTDIR'
fi

if [[ -f "${JETBRAINS_INTELLIJ_ZSH_DIR}/zsh-integration.zsh" ]]; then
  builtin source "${JETBRAINS_INTELLIJ_ZSH_DIR}/zsh-integration.zsh"
fi

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: after loading ${(%):-%x}"
fi
