#!/usr/bin/env bash

function foo() {
  echo "Some text"
  <caret>
  let "a = 3 + 5";
}