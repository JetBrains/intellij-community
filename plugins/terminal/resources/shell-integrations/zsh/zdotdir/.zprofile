# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# See doc in .zshenv for how IntelliJ injects itself into Zsh startup process.

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: before loading ${(%):-%x}"
fi

JETBRAINS_INTELLIJ_ORIGINAL_FILENAME_TO_SOURCE='.zprofile'
builtin source "$JETBRAINS_INTELLIJ_ZSH_DIR/zdotdir/source-original.zsh"

if [[ -n "${JETBRAINS_INTELLIJ_TERMINAL_DEBUG_LOG_LEVEL-}" ]]; then
  builtin echo "intellij: after loading ${(%):-%x}"
fi
