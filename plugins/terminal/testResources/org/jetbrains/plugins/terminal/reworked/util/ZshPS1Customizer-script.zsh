# Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

builtin autoload -Uz add-zsh-hook

function add_suffix_to_PS1() {
  if [[ "$PS1" != *",$INTELLIJ_PS1_SUFFIX" ]]; then
    PS1="$PS1,$INTELLIJ_PS1_SUFFIX"
  fi
  # re-add `add_suffix_to_PS1` to ensure it runs last
  add-zsh-hook -d precmd add_suffix_to_PS1
  add-zsh-hook precmd add_suffix_to_PS1
}

add-zsh-hook precmd add_suffix_to_PS1
