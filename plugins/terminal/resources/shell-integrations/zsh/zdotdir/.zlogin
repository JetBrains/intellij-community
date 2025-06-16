# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# See doc in .zshenv for how IntelliJ injects itself into Zsh startup process.

# Actually, this file is not expected to be be read, as ZDOTDIR is restored to its
# original value in the IntelliJ's .zshrc and IntelliJ always starts Zsh in an interactive
# mode, a behavior that, according to the documentation, cannot be modified afterward:
#
# INTERACTIVE shell state option (https://zsh.sourceforge.io/Doc/Release/Options.html)
#  "The value of this option can only be changed via flags supplied at invocation of
#  the shell. It cannot be changed once zsh is running."
#
# However, just in case, let's take a safety net and proxy .zlogin.

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: before loading ${(%):-%x}"
fi

if [[ -n "${JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR-}" ]]; then
  ZDOTDIR="$JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR"
  builtin unset 'JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR'
else
  # defaults ZDOTDIR to HOME
  builtin unset 'ZDOTDIR'
fi

JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE='.zlogin'
builtin source "$JETBRAINS_INTELLIJ_ZSH_DIR/zdotdir/source-original.zsh"

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: after loading ${(%):-%x}"
fi
