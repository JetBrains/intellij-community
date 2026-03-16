#!/usr/bin/env bash

set -euxo pipefail

CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_PLUGIN_DIR="$(cd "$CURRENT_DIR/.." && pwd)"

HELPERS_DIR="$PYTHON_PLUGIN_DIR/helpers"
PYCHARM_DIR="$HELPERS_DIR/pycharm"
PY3ONLY_DIR="$HELPERS_DIR/py3only"

export PYTHONPATH="$PY3ONLY_DIR:$PYCHARM_DIR:$HELPERS_DIR${PYTHONPATH:+:$PYTHONPATH}"

[ $# -eq 0 ] && set -- tests

for version in 3.9.25 3.10.19 3.11.14 3.12.12 3.13.12 3.14.3 3.15.0a5; do
  # execute new tests
  uv run --python "$version" pytest --junit-xml=.reports/junit-py$version.xml "$@"

  # execute legacy tests
  uv run --python "$version" legacy/__main__.py
done
