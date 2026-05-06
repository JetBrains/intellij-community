#!/usr/bin/env bash

set -euxo pipefail

for version in 3.9.25 3.10.20 3.11.15 3.12.13 3.13.13 3.14.5rc1 3.15.0a8; do
  uv run --python "$version" __main__.py
done
