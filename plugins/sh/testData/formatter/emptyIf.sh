#!/usr/bin/env bash

function foo() {
  echo "Some text"
  if [ -d $directory ]; then<caret>
  fi

}