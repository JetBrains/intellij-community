#!/usr/bin/env bash

function foo() {
  echo "Some text"
  if [ -d $directory ];
  then
    echo "Some text"
  elif [ $file1 -nt $file2 ];
  then
    <caret>

  fi

}