#!/bin/bash

function <caret>addVersion() {
  local cmd="$1"
  local expected="$2"
  local actual="$3"
  local argIndex="$4"
  local search="$((echo "$actual" | eval "awk '{print $"${argIndex}"}'" | grep "$expected") || true)"
  if [[ "$search" == "" ]]; then
    taskError "$cmd must be version $expected, detected $actual"
  fi
  taskDebug "$cmd is version $expected"
}

addVersion