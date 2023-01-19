#!/bin/sh

function addVersion() {
  local search="$((echo "$actual" | eval "awk '{print $"${argIndex}"}'" | grep "$expected") || true)"
  if [[ "$search" == "" ]]; then
    taskError "$cmd must be version $expected, detected $actual"
  fi
}