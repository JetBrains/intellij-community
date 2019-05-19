#!/bin/bash

function die() {
  echo "${1}"
  exit 1
}

which<caret> printf > /dev/null 2>&1 || die "Shell integration requires the printf binary to be in your path."
which which
