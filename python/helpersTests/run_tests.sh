#!/usr/bin/env bash

set -euxo pipefail

for version in 3.9.25 3.10.20 3.11.15 3.12.13 3.13.13 3.14.5 3.15.0b1; do
  uv run --python "$version" __main__.py
done

# PyCharm runs generator3 and remote_sync.py in the interpreter of the user, also on Python 3.8.
# The project dependencies need Python 3.9 or later, and these tests need only the helpers.
uv run --no-project --python 3.8.20 __main__.py discover --start-directory tests/generator3_tests --top-level-directory tests
PYTHONPATH=tests uv run --no-project --python 3.8.20 __main__.py test_remote_sync
