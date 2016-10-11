#!/bin/bash

# mappings for Ctrl-left-arrow and Ctrl-right-arrow for word moving
bindkey '^[^[[C' forward-word
bindkey '^[^[[D' backward-word

if [ -f "$HOME/.zshrc" ]; then
     source "$HOME/.zshrc"
  fi

if [ -n "$JEDITERM_SOURCE" ]
then
  source $JEDITERM_SOURCE
fi