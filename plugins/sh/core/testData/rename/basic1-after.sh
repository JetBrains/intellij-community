#!/bin/bash

SHELL=${SHELL##*/}
MY_NAME="${HOME}/.iterm2_shell_integration.${SHELL}"
chmod +x "${MY_NAME}"
