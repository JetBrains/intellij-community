# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# This is an entry point of IntelliJ Zsh shell integration.
# The goal is to source `zsh-integration.zsh` file after sourcing the user's ~/.zshrc.
# This ensures that IntelliJ's `precmd` hook is appended last to the `precmd_functions`
# array and therefore executed last. This allows control over the PS1 environment
# variable from the `precmd` hook even if other `precmd` hooks are also modifying it.

# According to http://zsh.sourceforge.net/Doc/Release/Files.html, zsh startup configuration files are read in this order:
# 1. /etc/zshenv
# 2. $ZDOTDIR/.zshenv
# 3. /etc/zprofile (if shell is login)
# 4. $ZDOTDIR/.zprofile (if shell is login)
# 5. /etc/zshrc (if shell is interactive)
# 6. $ZDOTDIR/.zshrc (if shell is interactive)
# 7. /etc/zlogin (if shell is login)
# 8. $ZDOTDIR/.zlogin (if shell is login)
#
# If ZDOTDIR is unset, HOME is used instead.

# IntelliJ launches zsh with a custom ZDOTDIR pointing to the parent directory of this file
# in order to source `zsh-integration.zsh` from the custom .zshrc.
# However, using a custom ZDOTDIR prevents the user's configuration files
# (~/.zshenv, ~/.zprofile, ~/.zshrc, ~/.zlogin) from being read.
# To address this, each user's configuration file is sourced manually from its custom counterpart.

# This file is read, because IntelliJ launches zsh with custom ZDOTDIR.

# Implementation notes on safe shell scripting:
# * Use `builtin` prefix to avoid accidentally calling user-defined functions / aliases.
# * Use ${var-default} or ${var:-default} to not fail in configurations with `setopt nounset`.
# * Use "${var}" to preserve whitespaces, ${var} will be split into words in configurations with `setopt sh_word_split`.

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  # ${(%):-%x} expands to the current script being executed

  # (%) is a parameter expansion flag enabling prompt expansion in the resulting value
  # https://zsh.sourceforge.io/Doc/Release/Expansion.html

  # %x "The name of the file containing the source code currently being executed."
  # https://zsh.sourceforge.io/Doc/Release/Prompt-Expansion.html

  builtin echo "intellij: before loading ${(%):-%x}"
fi

JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE='.zshenv'
builtin source "$JETBRAINS_INTELLIJ_ZSH_DIR/zdotdir/source-original.zsh"

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: after loading ${(%):-%x}"
fi
