#!/usr/bin/env bash

function foo() {
  echo "Some text"
  until [  ]; do
      <caret>
  done
  let "a = 3 + 5";
}