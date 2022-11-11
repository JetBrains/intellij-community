#!/usr/bin/env bash

function foo() {
  echo "Some<caret> text"
  let "a = 3 + 5";
}