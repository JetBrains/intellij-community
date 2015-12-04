#!/usr/bin/env bash
set -x

TEST_DATA_ROOT="${1:-$(pwd)}"
cd "$TEST_DATA_ROOT"

for name in $(ls); do
  if [[ -f "$name" ]]; then 
    if [[ "$name" =~ ^before.*\.py ]]; then
      withoutPrefix="${name#before}"
      git mv "$name" "${withoutPrefix,}"
    fi
    if [[ "$name" =~ ^after.*\.py ]]; then
      testName="${name%.py}"
      withoutPrefix="${testName#after}_after.py"
      git mv "$name" "${withoutPrefix,}"
    fi
  fi
done
