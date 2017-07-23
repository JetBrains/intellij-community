#!/bin/bash

# mappings for Ctrl-left-arrow and Ctrl-right-arrow for word moving
bindkey '^[^[[C' forward-word
bindkey '^[^[[D' backward-word

ZDOTDIR=$_OLD_ZDOTDIR

if [ -n "$JEDITERM_USER_RCFILE" ]
then
  source $JEDITERM_USER_RCFILE
  unset JEDITERM_USER_RCFILE
fi

if [ -n "$ZDOTDIR" ]
then
  DOTDIR=$ZDOTDIR
else
  DOTDIR=$HOME
fi

if [ -f "$DOTDIR/.zshenv" ]; then
     source "$DOTDIR/.zshenv"
fi

if [ -n $LOGIN_SHELL ]; then
  if [ -f "$DOTDIR/.zprofile" ]; then
       source "$DOTDIR/.zprofile"
  fi
fi

if [ -f "$DOTDIR/.zshrc" ]; then
     source "$DOTDIR/.zshrc"
fi

if [ -n $LOGIN_SHELL ]; then
  if [ -f "$DOTDIR/.zlogin" ]; then
       source "$DOTDIR/.zlogin"
  fi
fi

if [ -n "$JEDITERM_SOURCE" ]
then
  source $(echo $JEDITERM_SOURCE)
  unset JEDITERM_SOURCE
fi