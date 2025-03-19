#!/bin/bash
# Based on scripts/generate_proto_stubs.sh.
# Generates the protobuf stubs for the given tensorflow version using mypy-protobuf.
# Generally, new minor versions are a good time to update the stubs.

set -euxo pipefail

# Whenever you update TENSORFLOW_VERSION here, version should be updated
# in stubs/tensorflow/METADATA.toml and vice-versa.
TENSORFLOW_VERSION=2.16.1
MYPY_PROTOBUF_VERSION=3.6.0

# brew install coreutils wget
# sudo apt-get install -y unzip
REPO_ROOT="$(realpath "$(dirname "${BASH_SOURCE[0]}")"/..)"
TMP_DIR="$(mktemp -d)"
TENSORFLOW_FILENAME="v$TENSORFLOW_VERSION.zip"
TENSORFLOW_URL="https://github.com/tensorflow/tensorflow/archive/refs/tags/$TENSORFLOW_FILENAME"
TENSORFLOW_DIR="tensorflow-$TENSORFLOW_VERSION"

cd "$TMP_DIR"
echo "Working in $TMP_DIR"

# Fetch tensorflow (which contains all the .proto files)
wget "$TENSORFLOW_URL"
unzip "$TENSORFLOW_FILENAME"

# Prepare virtualenv
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install grpcio-tools pre-commit mypy-protobuf=="$MYPY_PROTOBUF_VERSION"

# Empty target folders or the mv command below will fail
rm -rf "$REPO_ROOT/stubs/tensorflow/tensorflow/tsl/"
rm -rf "$REPO_ROOT/stubs/tensorflow/tensorflow/compiler/xla/"
# Remove existing pyi
find "$REPO_ROOT/stubs/tensorflow/" -name "*_pb2.pyi" -delete

# Folders here cover the more commonly used protobufs externally and
# their dependencies. Tensorflow has more protobufs and can be added if requested.
PROTOC_VERSION=$(python3 -m grpc_tools.protoc --version)
echo $PROTOC_VERSION
python3 -m grpc_tools.protoc \
  --proto_path="$TENSORFLOW_DIR/third_party/xla/third_party/tsl" \
  --proto_path="$TENSORFLOW_DIR/third_party/xla" \
  --proto_path="$TENSORFLOW_DIR" \
  --mypy_out "relax_strict_optional_primitives:$REPO_ROOT/stubs/tensorflow" \
  $TENSORFLOW_DIR/third_party/xla/xla/*.proto \
  $TENSORFLOW_DIR/third_party/xla/xla/service/*.proto \
  $TENSORFLOW_DIR/tensorflow/core/example/*.proto \
  $TENSORFLOW_DIR/tensorflow/core/framework/*.proto \
  $TENSORFLOW_DIR/tensorflow/core/protobuf/*.proto \
  $TENSORFLOW_DIR/tensorflow/core/protobuf/tpu/*.proto \
  $TENSORFLOW_DIR/tensorflow/core/util/*.proto \
  $TENSORFLOW_DIR/tensorflow/python/keras/protobuf/*.proto \
  $TENSORFLOW_DIR/third_party/xla/third_party/tsl/tsl/protobuf/*.proto \

# Cleanup after ourselves, this is a temp dir, but it can still grow fast if run multiple times
rm -rf "$TMP_DIR"

# Must be in a git repository to run pre-commit
cd "$REPO_ROOT"

# Move third-party and fix imports
mv stubs/tensorflow/tsl/ stubs/tensorflow/tensorflow/
find stubs/tensorflow/ -name '*_pb2.pyi' | xargs sed -i "" -r "s/(\[|\s)tsl\./\1tensorflow\.tsl\./"
mv stubs/tensorflow/xla/ stubs/tensorflow/tensorflow/compiler/
find stubs/tensorflow/ -name '*_pb2.pyi' | xargs sed -i "" -r "s/(\[|\s)xla\./\1tensorflow\.compiler\.xla\./"

# These protos exist in a folder with protos used in python,
# but are not included in the python wheel.
# They are likely only used for other language builds.
# stubtest was used to identify them by looking for ModuleNotFoundError.
# (comment out ".*_pb2.*" from the allowlist)
rm -r \
  stubs/tensorflow/tensorflow/compiler/xla/autotune_results_pb2.pyi \
  stubs/tensorflow/tensorflow/compiler/xla/autotuning_pb2.pyi \
  stubs/tensorflow/tensorflow/compiler/xla/service/buffer_assignment_pb2.pyi \
  stubs/tensorflow/tensorflow/compiler/xla/service/hlo_execution_profile_data_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/autotuning_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/conv_autotuning_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/critical_section_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/eager_service_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/master_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/master_service_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/replay_log_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/tpu/compile_metadata_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/worker_pb2.pyi \
  stubs/tensorflow/tensorflow/core/protobuf/worker_service_pb2.pyi \
  stubs/tensorflow/tensorflow/core/util/example_proto_fast_parsing_test_pb2.pyi \

sed -i "" \
  "s/extra_description = .*$/extra_description = \"\
Partially generated using [mypy-protobuf==$MYPY_PROTOBUF_VERSION](https:\/\/github.com\/nipunn1313\/mypy-protobuf\/tree\/v$MYPY_PROTOBUF_VERSION) \
and $PROTOC_VERSION \
on tensorflow==$TENSORFLOW_VERSION .\"/" \
  stubs/tensorflow/METADATA.toml

# use `|| true` so the script still continues even if a pre-commit hook
# applies autofixes (which will result in a nonzero exit code)
pre-commit run --files $(git ls-files -- "stubs/tensorflow/**_pb2.pyi") || true
