from __future__ import annotations

import subprocess
import sys
from collections.abc import Iterable
from http.client import HTTPResponse
from pathlib import Path
from typing import TYPE_CHECKING
from urllib.request import urlopen
from zipfile import ZipFile

from mypy_protobuf.main import (  # type: ignore[import-untyped]  # pyright: ignore[reportMissingTypeStubs]
    __version__ as mypy_protobuf__version__,
)

if TYPE_CHECKING:
    from _typeshed import StrOrBytesPath, StrPath

MYPY_PROTOBUF_VERSION = mypy_protobuf__version__


def download_file(url: str, destination: Path) -> None:
    print(f"Downloading '{url}' to '{destination}'")
    resp: HTTPResponse
    with urlopen(url) as resp:
        destination.write_bytes(resp.read())


def extract_archive(archive_path: StrPath, destination: StrPath) -> None:
    print(f"Extracting '{archive_path}' to '{destination}'")
    with ZipFile(archive_path) as file_in:
        file_in.extractall(destination)


def run_protoc(
    proto_paths: Iterable[StrPath], mypy_out: StrPath, proto_globs: Iterable[str], cwd: StrOrBytesPath | None = None
) -> str:
    """TODO: Describe parameters and return."""
    protoc_version = (
        subprocess.run([sys.executable, "-m", "grpc_tools.protoc", "--version"], capture_output=True, check=False)
        .stdout.decode()
        .strip()
    )
    print()
    print(protoc_version)
    protoc_args = [
        *[f"--proto_path={proto_path}" for proto_path in proto_paths],
        "--mypy_out",
        f"relax_strict_optional_primitives:{mypy_out}",
        *proto_globs,
    ]
    print("Running: protoc\n    " + "\n    ".join(protoc_args) + "\n")
    subprocess.run((sys.executable, "-m", "grpc_tools.protoc", *protoc_args), cwd=cwd, check=True)
    return protoc_version
