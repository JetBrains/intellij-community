#!/usr/bin/env bash

set -euxo pipefail

for version in 3.9.25 3.10.19 3.11.14 3.12.12 3.13.12 3.14.3 3.15.0a5; do
  uv run --python "$version" __main__.py
done
