# Injecting code in user Zsh rc files is a delicate business.
# We prefix all builtins to avoid accidentally calling into
# user-defined functions. Up until we disable alias expansion
# we also quote every word to prevent accidental alias
# expansion. We also clean up after ourselves so that no
# termporary parameters or functions are left over once we are
# done.

ZDOTDIR="$_OLD_ZDOTDIR"
'builtin' 'unset' '_OLD_ZDOTDIR'

# Bind Ctrl+Left and Ctrl+Right in the main keymap (usually emacs)
# to cursor movement by words.
'builtin' 'bindkey' '^[^[[C' 'forward-word'
'builtin' 'bindkey' '^[^[[D' 'backward-word'

if [[ -n "$JEDITERM_USER_RCFILE" ]]; then
  'builtin' 'source' '--' "$JEDITERM_USER_RCFILE"
fi
'builtin' 'unset' 'JEDITERM_USER_RCFILE'
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
  if [[ -n "$JEDITERM_SOURCE" ]]; then
    # TODO: Is it correct to split JEDITERM_SOURCE_ARGS on IFS and
    # drop empty arguments? Bash integration does it and it looks
    # intentional.
    builtin source -- "$JEDITERM_SOURCE" ${=JEDITERM_SOURCE_ARGS}
  fi
  builtin unset JEDITERM_SOURCE JEDITERM_SOURCE_ARGS

  # Enable native zsh options to make coding easier.
  builtin emulate -L zsh
  builtin zmodload zsh/parameter 2>/dev/null

  builtin local var
  # For every _INTELLIJ_FORCE_SET_FOO=BAR run: export FOO=BAR.
  for var in ${parameters[(I)_INTELLIJ_FORCE_SET_*]}; do
    builtin export ${var:20}=${(P)var}
    builtin unset $var
  done
  # For every _INTELLIJ_FORCE_PREPEND_FOO=BAR run: export FOO=BAR$FOO.
  for var in ${parameters[(I)_INTELLIJ_FORCE_PREPEND_*]}; do
    builtin local name=${var:24}
    builtin export $name=${(P)var}${(P)name}
    builtin unset $var
  done

  # Remove the hook and the function.
  builtin typeset -ga precmd_functions
  precmd_functions=(${precmd_functions:#_jedi_precmd_hook})
  builtin unset -f _jedi_precmd_hook
}

builtin typeset -ga precmd_functions
precmd_functions+=(_jedi_precmd_hook)

(( _jedi_restore_aliases )) && builtin setopt aliases
'builtin' 'unset' '_jedi_restore_aliases'
