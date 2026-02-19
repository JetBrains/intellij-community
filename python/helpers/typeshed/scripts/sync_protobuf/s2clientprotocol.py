#!/usr/bin/env python3
"""
Generates the protobuf stubs for the given s2clientprotocol version using mypy-protobuf.
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
from ts_utils.metadata import update_metadata
from ts_utils.paths import distribution_path

# Whenever you update PACKAGE_VERSION here, version should be updated
# in stubs/s2clientprotocol/METADATA.toml and vice-versa.
PACKAGE_VERSION = "c04df4adbe274858a4eb8417175ee32ad02fd609"

STUBS_FOLDER = distribution_path("s2clientprotocol").absolute()
ARCHIVE_FILENAME = f"{PACKAGE_VERSION}.zip"
ARCHIVE_URL = f"https://github.com/Blizzard/s2client-proto/archive/{ARCHIVE_FILENAME}"
EXTRACTED_PACKAGE_DIR = f"s2client-proto-{PACKAGE_VERSION}"

VERSION_PATTERN = re.compile(r'def game_version\(\):\n    return "(.+?)"')


def extract_python_version(file_path: Path) -> str:
    """Extract Python version from s2clientprotocol's build file."""
    match = re.search(VERSION_PATTERN, file_path.read_text())
    assert match
    return match.group(1)


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
        proto_paths=(EXTRACTED_PACKAGE_DIR,),
        mypy_out=STUBS_FOLDER,
        proto_globs=(f"{EXTRACTED_PACKAGE_DIR}/s2clientprotocol/*.proto",),
        cwd=temp_dir,
    )

    python_s2_client_proto_version = extract_python_version(temp_dir / EXTRACTED_PACKAGE_DIR / "s2clientprotocol" / "build.py")

    # Cleanup after ourselves, this is a temp dir, but it can still grow fast if run multiple times
    shutil.rmtree(temp_dir)

    update_metadata(
        "s2clientprotocol",
        extra_description=f"""Partially generated using \
[mypy-protobuf=={MYPY_PROTOBUF_VERSION}](https://github.com/nipunn1313/mypy-protobuf/tree/v{MYPY_PROTOBUF_VERSION}) \
and {protoc_version} on \
[s2client-proto {python_s2_client_proto_version}](https://github.com/Blizzard/s2client-proto/tree/{PACKAGE_VERSION}).""",
    )
    print("Updated s2clientprotocol/METADATA.toml")

    # Run pre-commit to cleanup the stubs
    subprocess.run((sys.executable, "-m", "pre_commit", "run", "--files", *STUBS_FOLDER.rglob("*_pb2.pyi")), check=False)


if __name__ == "__main__":
    main()
