# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# This is a helper script for loading Zsh user configuration files in the global scope.
# How to use it: 
# 1. Set JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE to the name of the configuration file to load.
# 2. Source this script to load the file in the global Zsh scope.

# We are in the global scope => prefix variables with `JETBRAINS_INTELLIJ_`.

JETBRAINS_INTELLIJ_ORIGINAL_FILE="${JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR:-$HOME}/$JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE"

if [[ -f "$JETBRAINS_INTELLIJ_ORIGINAL_FILE" ]]; then
  # prevent recursion, just in case
  if [[ "$ZDOTDIR" != "${JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR:-$HOME}" ]]; then
    JETBRAINS_INTELLIJ_ZDOTDIR_COPY="$ZDOTDIR"

    # Correct ZDOTDIR before sourcing the user's file as it might rely on the value of ZDOTDIR.
    if [[ -n "$JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR" ]]; then
      ZDOTDIR="$JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR"
    else
      builtin unset ZDOTDIR # defaults ZDOTDIR to HOME
    fi

    if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
      builtin echo "intellij: loading $JETBRAINS_INTELLIJ_ORIGINAL_FILE"
    fi

    builtin source "$JETBRAINS_INTELLIJ_ORIGINAL_FILE"

    # ZDOTDIR might be changed by the user config
    if [[ -n "$ZDOTDIR" ]]; then
      JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR="$ZDOTDIR"
    else
      builtin unset JETBRAINS_INTELLIJ_ORIGINAL_ZDOTDIR
    fi

    # Set back to the IntelliJ location to continue injecting IntelliJ shell integration.
    ZDOTDIR="$JETBRAINS_INTELLIJ_ZDOTDIR_COPY"
  fi
fi

builtin unset JETBRAINS_INTELLIJ_ORIGINAL_FILE
builtin unset JETBRAINS_INTELLIJ_ZDOTDIR_COPY
builtin unset JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE
