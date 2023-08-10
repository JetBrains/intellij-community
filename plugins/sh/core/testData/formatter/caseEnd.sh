#!/usr/bin/env bash

function foo() {
  echo "Some text"
  if [ -d $directory ];
  then
    echo "Some text"
  elif [ $file1 -nt $file2 ]; then
    echo "Some text"
  else
    let "a = 3 + 5";
  fi
  case $x in
  *)
    echo "Example"
    ;;<caret>
  esac
}