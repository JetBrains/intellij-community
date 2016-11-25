if test -f ~/.config/fish/config.fish
  . ~/.config/fish/config.fish
end

if test -n "$JEDITERM_USER_RCFILE"
  . $JEDITERM_USER_RCFILE
  set -e JEDITERM_USER_RCFILE
end

if test -n "$JEDITERM_SOURCE"
  . $JEDITERM_SOURCE
  set -e JEDITERM_SOURCE
end