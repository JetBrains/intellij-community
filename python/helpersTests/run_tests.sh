#!/usr/bin/env bash

set -euxo pipefail

for version in 3.9.25 3.10.20 3.11.15 3.12.13 3.13.13 3.14.5 3.15.0b1; do
  uv run --python "$version" __main__.py
done
