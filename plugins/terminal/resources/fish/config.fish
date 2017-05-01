if test -n "$OLD_XDG_CONFIG_HOME"
  set XDG_CONFIG_HOME "$OLD_XDG_CONFIG_HOME"
else
  set -e XDG_CONFIG_HOME
end

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