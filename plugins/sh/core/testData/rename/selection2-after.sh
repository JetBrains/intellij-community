#!/bin/zsh

test -f "${HOME}/.zsh_profile" && SCRIPT="${HOME}/.zsh_profile" || SCRIPT="${HOME}/.profile"
echo "$SCRIPT"
