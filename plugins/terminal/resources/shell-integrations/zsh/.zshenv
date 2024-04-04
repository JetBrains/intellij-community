# Injecting code in user Zsh rc files is a delicate business.
# We prefix all builtins to avoid accidentally calling into
# user-defined functions. Up until we disable alias expansion
# we also quote every word to prevent accidental alias
# expansion. We also clean up after ourselves so that no
# temporary parameters or functions are left over once we are
# done.
#
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

# This file is read, because IntelliJ launches zsh with custom ZDOTDIR.

# Restore ZDOTDIR original value to load further zsh startup files correctly.
if [[ -n "$_INTELLIJ_ORIGINAL_ZDOTDIR" ]]; then
  ZDOTDIR="$_INTELLIJ_ORIGINAL_ZDOTDIR"
  'builtin' 'unset' '_INTELLIJ_ORIGINAL_ZDOTDIR'
else
  # defaults ZDOTDIR to HOME
  'builtin' 'unset' 'ZDOTDIR'
fi

# Source original $ZDOTDIR/.zshenv manually
if [[ -f "${ZDOTDIR:-$HOME}"/.zshenv ]]; then
  'builtin' 'source' '--' "${ZDOTDIR:-$HOME}"/.zshenv
fi

# Stop right here if this is not interactive shell.
[[ -o 'interactive' ]] || 'builtin' 'return' '0'

'builtin' 'typeset' '-i' '_jedi_restore_aliases'
if [[ -o 'aliases' ]]; then
  'builtin' 'setopt' 'no_aliases'
  # No need to quote everything after this point because aliases
  # are disabled. We didn't do this earlier because we must source
  # the user's .zshenv with the default shell options.
  _jedi_restore_aliases=1
fi

# This function will be called after all rc files are processed
# and before the first prompt is displayed.
function _jedi_precmd_hook() {
  # Remove the hook and the functions.
  builtin typeset -ga precmd_functions
  precmd_functions=(${precmd_functions:#_jedi_precmd_hook})
  builtin unset -f _jedi_precmd_hook

  builtin local integration_main="${JETBRAINS_INTELLIJ_ZSH_DIR}/zsh-integration.zsh"
  [ -r "$integration_main" ] && builtin source "$integration_main"
}

builtin typeset -ga precmd_functions
precmd_functions+=(_jedi_precmd_hook)

# Disable p10k Instant Prompt feature: https://github.com/romkatv/powerlevel10k#instant-prompt
# because it breaks our command blocks integration by showing the prompt immediately before '.zshrc' is fully sourced
if [ -n "${INTELLIJ_TERMINAL_COMMAND_BLOCKS:-}" ]
then
  builtin typeset -g POWERLEVEL9K_INSTANT_PROMPT=off
fi

(( _jedi_restore_aliases )) && builtin setopt aliases
'builtin' 'unset' '_jedi_restore_aliases'
