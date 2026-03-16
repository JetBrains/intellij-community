#!/usr/bin/env bash

set -euo pipefail

CURRENT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_PLUGIN_DIR="$(cd "$CURRENT_DIR/.." && pwd)"

HELPERS_DIR="$PYTHON_PLUGIN_DIR/helpers"
PYCHARM_DIR="$HELPERS_DIR/pycharm"
PY3ONLY_DIR="$HELPERS_DIR/py3only"

export PYTHONPATH="$PY3ONLY_DIR:$PYCHARM_DIR:$HELPERS_DIR${PYTHONPATH:+:$PYTHONPATH}"

python2 -m pytest --junit-xml=.tox/reports/junit-py27.xml tests
