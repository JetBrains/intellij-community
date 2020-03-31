#!/usr/bin/env bash

function foo() {
  echo "Some text"
  if [ -d $directory ]; then
     echo "Some text example" >&2
     for (( i = 0; i < n; i++ )); do
         <caret>
     done
     echo "priority, minus is higher" >&2
     exit 1
  else
    let "a = 3 + 5";
  fi
}