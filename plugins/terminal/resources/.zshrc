#!/bin/bash

# mappings for Ctrl-left-arrow and Ctrl-right-arrow for word moving
bindkey '^[^[[C' forward-word
bindkey '^[^[[D' backward-word

ZDOTDIR=$_OLD_ZDOTDIR

if [ -n "$JEDITERM_USER_RCFILE" ]
then
  source "$JEDITERM_USER_RCFILE"
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
  source $(echo $JEDITERM_SOURCE) $JEDITERM_SOURCE_ARGS
  unset JEDITERM_SOURCE
  unset JEDITERM_SOURCE_ARGS
fi

function override_jb_variables {
  env | while IFS="=" read NAME VALUE
  do
    if [[ $NAME = '_INTELLIJ_FORCE_SET_'* ]]
    then
      NEW_NAME=${NAME:20}
      if [ -n "$NEW_NAME" ]
      then
        export "$NEW_NAME"="$VALUE"
      fi
    fi
  done
}

override_jb_variables
