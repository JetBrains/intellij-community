#!/bin/bash

if [ -n "$LOGIN_SHELL" ]; then
  unset LOGIN_SHELL

  #       When bash is invoked as an interactive login shell, or as a  non-interac-
  #       tive  shell with the --login option, it first reads and executes commands
  #       from the file /etc/profile, if that  file  exists.   After  reading  that
  #       file,  it  looks  for  ~/.bash_profile, ~/.bash_login, and ~/.profile, in
  #       that order, and reads and executes  commands  from  the  first  one  that
  #       exists  and  is  readable.

  if [ -f /etc/profile ]; then
     source /etc/profile
  fi

  if [ -f ~/.bash_profile ]; then
    source ~/.bash_profile
  else
    if [ -f ~/.bash_login ]; then
      source ~/.bash_login
    else
      if [ -f ~/.profile ]; then
        source ~/.profile
      fi
    fi
  fi
else
  if [ -f ~/.bashrc ]; then
    source ~/.bashrc
  fi
fi

function disable_posix() {
  if shopt -qo posix
  then
    set +o posix
    __jetbrains_intellij_restore_posix_flag=1
  fi
}

function restore_posix() {
  if [ -n "${__jetbrains_intellij_restore_posix_flag-}" ]
  then
    set -o posix
    unset __jetbrains_intellij_restore_posix_flag
  fi
}

# Disable posix for overriding ENV
disable_posix

function override_jb_variables {
  while read VARIABLE
  do
    NAME=${VARIABLE%%=*}
    if [[ $NAME = '_INTELLIJ_FORCE_SET_'* ]]
    then
      NEW_NAME=${NAME:20}
      if [ -n "$NEW_NAME" ]
      then
        VALUE=${VARIABLE#*=}
        export "$NEW_NAME"="$VALUE"
      fi
    fi
    if [[ $NAME = '_INTELLIJ_FORCE_PREPEND_'* ]]
    then
      NEW_NAME=${NAME:24}
      if [ -n "$NEW_NAME" ]
      then
        VALUE=${VARIABLE#*=}
        export "$NEW_NAME"="$VALUE${!NEW_NAME}"
      fi
    fi
  done < <(env)
}

override_jb_variables

# mappings for Ctrl-left-arrow and Ctrl-right-arrow for word moving
bind '"\e\e[C":forward-word'
bind '"\e\e[D": backward-word'
bind '"\e\O[C":forward-word'
bind '"\e\O[D": backward-word'

# Restore posix again, as we are going to source user RC file next
restore_posix

if [ -n "${JEDITERM_USER_RCFILE-}" ]
then
  source "$JEDITERM_USER_RCFILE"
  unset JEDITERM_USER_RCFILE
fi

# Disable posix for the rest of the script
disable_posix

if [ -n "${JEDITERM_SOURCE-}" ]
then # JEDITERM_SOURCE_ARGS might be either list of args or one arg depending on JEDITERM_SOURCE_SINGLE_ARG
  if [ -n "${JEDITERM_SOURCE_SINGLE_ARG}" ]; then
      source "$JEDITERM_SOURCE" "${JEDITERM_SOURCE_ARGS}"
  else
      source "$JEDITERM_SOURCE" ${JEDITERM_SOURCE_ARGS-}
  fi
  unset JEDITERM_SOURCE
  unset JEDITERM_SOURCE_ARGS
fi

function configureCommandHistory {
  local commandHistoryFile="$__INTELLIJ_COMMAND_HISTFILE__"
  unset __INTELLIJ_COMMAND_HISTFILE__
  if [ -n "$commandHistoryFile" ] && [ -z "`trap -p EXIT`" ]
  then
    trap "$(builtin printf 'history -w %q; HISTFILE=%q' "$commandHistoryFile" "$HISTFILE")" EXIT
    if [ -s "$commandHistoryFile" ]
    then
      HISTFILE="$commandHistoryFile"
    fi
  fi
}
configureCommandHistory

JETBRAINS_INTELLIJ_BASH_DIR="$(dirname "${BASH_SOURCE[0]}")"
if [ -r "${JETBRAINS_INTELLIJ_BASH_DIR}/command-block-support.bash" ]; then
  source "${JETBRAINS_INTELLIJ_BASH_DIR}/command-block-support.bash"
fi
if [ -r "${JETBRAINS_INTELLIJ_BASH_DIR}/command-block-support-reworked.bash" ]; then
  source "${JETBRAINS_INTELLIJ_BASH_DIR}/command-block-support-reworked.bash"
fi
unset JETBRAINS_INTELLIJ_BASH_DIR

# Should be the last lines!
restore_posix
unset -f disable_posix
unset -f restore_posix