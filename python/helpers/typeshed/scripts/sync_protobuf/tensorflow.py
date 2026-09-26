#!/usr/bin/env python3
"""
Generates the protobuf stubs for the given tensorflow version using mypy-protobuf.
Generally, new minor versions are a good time to update the stubs.
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from _utils import MYPY_PROTOBUF_VERSION, download_file, extract_archive, run_protoc
from ts_utils.metadata import read_metadata, update_metadata
from ts_utils.paths import distribution_path

PACKAGE_VERSION = read_metadata("tensorflow").version_spec.version

STUBS_FOLDER = distribution_path("tensorflow").absolute()
ARCHIVE_FILENAME = f"v{PACKAGE_VERSION}.zip"
ARCHIVE_URL = f"https://github.com/tensorflow/tensorflow/archive/refs/tags/{ARCHIVE_FILENAME}"
EXTRACTED_PACKAGE_DIR = f"tensorflow-{PACKAGE_VERSION}"

PROTOS_TO_REMOVE = (
    "compiler/xla/autotune_results_pb2.pyi",
    "compiler/xla/autotuning_pb2.pyi",
    "compiler/xla/service/buffer_assignment_pb2.pyi",
    "compiler/xla/service/hlo_execution_profile_data_pb2.pyi",
    "core/protobuf/autotuning_pb2.pyi",
    "core/protobuf/conv_autotuning_pb2.pyi",
    "core/protobuf/critical_section_pb2.pyi",
    "core/protobuf/eager_service_pb2.pyi",
    "core/protobuf/master_pb2.pyi",
    "core/protobuf/master_service_pb2.pyi",
    "core/protobuf/replay_log_pb2.pyi",
    "core/protobuf/tpu/compile_metadata_pb2.pyi",
    "core/protobuf/worker_pb2.pyi",
    "core/protobuf/worker_service_pb2.pyi",
    "core/util/example_proto_fast_parsing_test_pb2.pyi",
)
"""
These protos exist in a folder with protos used in python,
but are not included in the python wheel.
They are likely only used for other language builds.
stubtest was used to identify them by looking for ModuleNotFoundError.
(comment out ".*_pb2.*" from the allowlist)
"""

TSL_IMPORT_PATTERN = re.compile(r"(\[|\s)tsl\.")
XLA_IMPORT_PATTERN = re.compile(r"(\[|\s)xla\.")


def move_tree(source: Path, destination: Path) -> None:
    """Move directory and merge if destination already exists.

    Can't use shutil.move because it can't merge existing directories.
    """
    print(f"Moving '{source}' to '{destination}'")
    shutil.copytree(source, destination, dirs_exist_ok=True)
    shutil.rmtree(source)


def post_creation() -> None:
    """Move third-party and fix imports."""
    print()
    move_tree(STUBS_FOLDER / "tsl", STUBS_FOLDER / "tensorflow" / "tsl")
    move_tree(STUBS_FOLDER / "xla", STUBS_FOLDER / "tensorflow" / "compiler" / "xla")

    for path in STUBS_FOLDER.rglob("*_pb2.pyi"):
        print(f"Fixing imports in '{path}'")
        filedata = path.read_text(encoding="utf-8")

        # Replace the target string
        filedata = re.sub(TSL_IMPORT_PATTERN, "\\1tensorflow.tsl.", filedata)
        filedata = re.sub(XLA_IMPORT_PATTERN, "\\1tensorflow.compiler.xla.", filedata)

        # Write the file out again
        path.write_text(filedata, encoding="utf-8")

    print()
    for to_remove in PROTOS_TO_REMOVE:
        file_path = STUBS_FOLDER / "tensorflow" / to_remove
        file_path.unlink()
        print(f"Removed '{file_path}'")


def main() -> None:
    temp_dir = Path(tempfile.mkdtemp())
    # Fetch tensorflow (which contains all the .proto files)
    archive_path = temp_dir / ARCHIVE_FILENAME
    download_file(ARCHIVE_URL, archive_path)
    extract_archive(archive_path, temp_dir)

    # Remove existing pyi
    for old_stub in STUBS_FOLDER.rglob("*_pb2.pyi"):
        old_stub.unlink()

    protoc_version = run_protoc(
        proto_paths=(
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla/third_party/tsl",
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla",
            f"{EXTRACTED_PACKAGE_DIR}",
        ),
        mypy_out=STUBS_FOLDER,
        proto_globs=(
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla/xla/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla/xla/service/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla/xla/tsl/protobuf/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/core/example/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/core/framework/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/core/protobuf/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/core/protobuf/tpu/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/core/util/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/tensorflow/python/keras/protobuf/*.proto",
            f"{EXTRACTED_PACKAGE_DIR}/third_party/xla/third_party/tsl/tsl/protobuf/*.proto",
        ),
        cwd=temp_dir,
    )

    # Cleanup after ourselves, this is a temp dir, but it can still grow fast if run multiple times
    shutil.rmtree(temp_dir)

    post_creation()

    update_metadata(
        "tensorflow",
        extra_description=f"""Partially generated using \
[mypy-protobuf=={MYPY_PROTOBUF_VERSION}](https://github.com/nipunn1313/mypy-protobuf/tree/v{MYPY_PROTOBUF_VERSION}) \
and {protoc_version} on `tensorflow=={PACKAGE_VERSION}`.""",
    )
    print("Updated tensorflow/METADATA.toml")

    # Run pre-commit to cleanup the stubs
    subprocess.run((sys.executable, "-m", "pre_commit", "run", "--files", *STUBS_FOLDER.rglob("*_pb2.pyi")), check=False)


if __name__ == "__main__":
    main()
