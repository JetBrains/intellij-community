#!/usr/bin/env bash

function foo() {
  echo "Some text"
  while [  ]; do
      <caret>
  done
  let "a = 3 + 5";
}