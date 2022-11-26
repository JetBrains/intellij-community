#!/bin/<selection>bash<caret></selection>

test -f "${HOME}/.bash_profile" && SCRIPT="${HOME}/.bash_profile" || SCRIPT="${HOME}/.profile"
echo "$SCRIPT"
