#!/bin/bash
# Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

set -euxo pipefail

# Whenever you update S2CLIENT_PROTO_VERSION here, version should be updated
# in stubs/s2clientprotocol/METADATA.toml and vice-versa.
S2CLIENT_PROTO_VERSION=c04df4adbe274858a4eb8417175ee32ad02fd609
MYPY_PROTOBUF_VERSION=3.6.0

REPO_ROOT="$(realpath "$(dirname "${BASH_SOURCE[0]}")"/..)"
TMP_DIR="$(mktemp -d)"
S2CLIENT_PROTO_FILENAME="$S2CLIENT_PROTO_VERSION.zip"
S2CLIENT_PROTO_URL="https://github.com/Blizzard/s2client-proto/archive/$S2CLIENT_PROTO_FILENAME"
S2CLIENT_PROTO_DIR="s2client-proto-$S2CLIENT_PROTO_VERSION"

cd "$TMP_DIR"
echo "Working in $TMP_DIR"

# Fetch s2clientprotocol (which contains all the .proto files)
wget "$S2CLIENT_PROTO_URL"
unzip "$S2CLIENT_PROTO_FILENAME"

# Prepare virtualenv
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install pre-commit mypy-protobuf=="$MYPY_PROTOBUF_VERSION"

# Remove existing pyi
find "$REPO_ROOT/stubs/s2clientprotocol/" -name "*_pb2.pyi" -delete

# s2client works on very old protoc versions, down to 2.6. So we can use the system's protoc.
PROTOC_VERSION=$(protoc --version)
echo $PROTOC_VERSION
protoc \
  --proto_path="$S2CLIENT_PROTO_DIR" \
  --mypy_out "relax_strict_optional_primitives:$REPO_ROOT/stubs/s2clientprotocol" \
  $S2CLIENT_PROTO_DIR/s2clientprotocol/*.proto \

PYTHON_S2CLIENT_PROTO_VERSION=$(
  grep -Pzo 'def game_version\(\):\n    return ".+?"' $S2CLIENT_PROTO_DIR/s2clientprotocol/build.py \
  | tr '\n' ' ' \
  | cut -d '"' -f 2
)

# Cleanup after ourselves, this is a temp dir, but it can still grow fast if run multiple times
rm -rf "$TMP_DIR"
# Must be in a git repository to run pre-commit
cd "$REPO_ROOT"

sed -i "" \
  "s/extra_description = .*$/extra_description = \"\
Partially generated using [mypy-protobuf==$MYPY_PROTOBUF_VERSION](https:\/\/github.com\/nipunn1313\/mypy-protobuf\/tree\/v$MYPY_PROTOBUF_VERSION) \
and $PROTOC_VERSION \
on [s2client-proto $PYTHON_S2CLIENT_PROTO_VERSION](https:\/\/github.com\/Blizzard\/s2client-proto\/tree\/$S2CLIENT_PROTO_VERSION)\"/" \
  stubs/s2clientprotocol/METADATA.toml

# use `|| true` so the script still continues even if a pre-commit hook
# applies autofixes (which will result in a nonzero exit code)
pre-commit run --files $(git ls-files -- "stubs/s2clientprotocol/**_pb2.pyi") || true
