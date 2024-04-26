# Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

# IF YOU ARE FROM FIG TEAM DO NOT HACK THIS
# Please go to issue tracker to discuss https://youtrack.jetbrains.com/issues

# Fig breaks shell integration. E.g. v2.17.0 incorrectly operates with bash-preexec and completely break preeexec functionality.
# This is especially bad for Fleet. Even though it doesn't seem to break critical parts of IntelliJ shell integration,
# Fig isn't needed in the block terminal anyway. Let's remove it in both IDEs to have a more stable shell integration.

# Remove fig from preexec
for index in "${!precmd_functions[@]}"; do
  if [[ "${precmd_functions[$index]}" == "__fig_"* ]]; then
    unset -v 'precmd_functions[$index]'
  fi
done

# Remove fig from precmd
for index in "${!preexec_functions[@]}"; do
  if [[ "${preexec_functions[$index]}" == "__fig_"* ]]; then
    unset -v 'preexec_functions[$index]'
  fi
done

# Fully remove fig integration
PROMPT_COMMAND="${PROMPT_COMMAND/__fig_post_prompt$'\n'/}"

# Fig may call '__bp_install_after_session_init' twice and next happened:
# 1. PROMPT_COMMAND contains double '__bp_install_string'
# 2. '__bp_install_string' contains:
#      trap - DEBUG
#      __bp_install
# 3. When '__bp_install' called first time all is fine
# 4. When '__bp_install' called second time it assumes that everything is initialized already and returns,
#    but previous 'trap - DEBUG' already removed DEBUG trap, so there is no any trap anymore
__bp_broken_string=$__bp_install_string$'\n'$__bp_install_string
PROMPT_COMMAND="${PROMPT_COMMAND//$__bp_broken_string/$__bp_install_string}"
unset __bp_broken_string