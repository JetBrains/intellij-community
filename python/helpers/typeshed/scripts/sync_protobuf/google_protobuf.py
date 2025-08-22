#!/usr/bin/env python3
"""
Generates the protobuf stubs for the given protobuf version using mypy-protobuf.
Generally, new minor versions are a good time to update the stubs.
"""

from __future__ import annotations

import json
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

from _utils import MYPY_PROTOBUF_VERSION, download_file, extract_archive, run_protoc
from ts_utils.metadata import read_metadata, update_metadata
from ts_utils.paths import distribution_path

# PyPi version has an extra "major" number that the Git version doesn't have
PACKAGE_VERSION = read_metadata("protobuf").version_spec.version[2:]

STUBS_FOLDER = distribution_path("protobuf").absolute()
ARCHIVE_FILENAME = f"protobuf-{PACKAGE_VERSION}.zip"
ARCHIVE_URL = f"https://github.com/protocolbuffers/protobuf/releases/download/v{PACKAGE_VERSION}/{ARCHIVE_FILENAME}"
EXTRACTED_PACKAGE_DIR = f"protobuf-{PACKAGE_VERSION}"

VERSION_PATTERN = re.compile(r'def game_version\(\):\n    return "(.+?)"')
PROTO_FILE_PATTERN = re.compile(r'"//:(.*)_proto"')


def extract_python_version(file_path: Path) -> str:
    """Extract the Python version from https://github.com/protocolbuffers/protobuf/blob/main/version.json ."""
    with file_path.open() as file:
        data: dict[str, Any] = json.load(file)
    # The root key will be the protobuf source code version
    version = next(iter(data.values()))["languages"]["python"]
    assert isinstance(version, str)
    return version


def extract_proto_file_paths(temp_dir: Path) -> list[str]:
    """
    Roughly reproduce the subset of .proto files on the public interface
    as described in py_proto_library calls in
    https://github.com/protocolbuffers/protobuf/blob/main/python/dist/BUILD.bazel .
    """
    with (temp_dir / EXTRACTED_PACKAGE_DIR / "python" / "dist" / "BUILD.bazel").open() as file:
        matched_lines = filter(None, (re.search(PROTO_FILE_PATTERN, line) for line in file))
        proto_files = [
            EXTRACTED_PACKAGE_DIR + "/src/google/protobuf/" + match.group(1).replace("compiler_", "compiler/") + ".proto"
            for match in matched_lines
        ]
    return proto_files


def main() -> None:
    temp_dir = Path(tempfile.mkdtemp())
    # Fetch s2clientprotocol (which contains all the .proto files)
    archive_path = temp_dir / ARCHIVE_FILENAME
    download_file(ARCHIVE_URL, archive_path)
    extract_archive(archive_path, temp_dir)

    # Remove existing pyi
    for old_stub in STUBS_FOLDER.rglob("*_pb2.pyi"):
        old_stub.unlink()

    protoc_version = run_protoc(
        proto_paths=(f"{EXTRACTED_PACKAGE_DIR}/src",),
        mypy_out=STUBS_FOLDER,
        proto_globs=extract_proto_file_paths(temp_dir),
        cwd=temp_dir,
    )

    python_protobuf_version = extract_python_version(temp_dir / EXTRACTED_PACKAGE_DIR / "version.json")

    # Cleanup after ourselves, this is a temp dir, but it can still grow fast if run multiple times
    shutil.rmtree(temp_dir)

    update_metadata(
        "protobuf",
        extra_description=f"""Partially generated using \
[mypy-protobuf=={MYPY_PROTOBUF_VERSION}](https://github.com/nipunn1313/mypy-protobuf/tree/v{MYPY_PROTOBUF_VERSION}) \
and {protoc_version} on \
[protobuf v{PACKAGE_VERSION}](https://github.com/protocolbuffers/protobuf/releases/tag/v{PACKAGE_VERSION}) \
(python `protobuf=={python_protobuf_version}`).""",
    )
    print("Updated protobuf/METADATA.toml")

    # Run pre-commit to cleanup the stubs
    subprocess.run((sys.executable, "-m", "pre_commit", "run", "--files", *STUBS_FOLDER.rglob("*_pb2.pyi")), check=False)


if __name__ == "__main__":
    main()
