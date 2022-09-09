#!/usr/bin/env bash

function foo() {
  echo "Some text"
  if [ -d $directory ]; then
     echo "Some<caret> text example" >&2
     echo "priority, minus is higher" >&2
     exit 1
  else
    let "a = 3 + 5";
  fi
}