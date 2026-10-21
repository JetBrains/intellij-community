#!/usr/bin/env python3
"""Run ty on typeshed's stdlib and third-party stubs."""

from __future__ import annotations

import argparse
import subprocess
import tempfile
from pathlib import Path

from ts_utils.paths import STDLIB_PATH, STUBS_PATH, TS_BASE_PATH
from ts_utils.stubs import path_stubs, stdlib_stubs, third_party_stubs

SUPPORTED_VERSIONS = ("3.10", "3.11", "3.12", "3.13", "3.14", "3.15")
SUPPORTED_PLATFORMS = ("linux", "darwin", "win32")
# requests is obsolete and the typed runtime package is installed instead.
EXCLUDED_STUBS = {"requests"}


def stdlib_files(version: str) -> list[Path]:
    """Return the stdlib stubs available in the requested Python version."""
    # ty cannot resolve relative imports in the legacy distutils stubs.
    return [stub.path for stub in stdlib_stubs(version) if stub.module_parts[0] != "distutils"]


def third_party_files() -> list[Path]:
    return [stub.path for stub in third_party_stubs() if stub.upstream_distribution not in EXCLUDED_STUBS]


def _filter_files(files: list[Path], paths: list[Path], root: Path) -> list[Path]:
    selected_paths = [path if path.is_absolute() else TS_BASE_PATH / path for path in paths]
    selected_files = {file for path in selected_paths if path.is_relative_to(root) for file in path_stubs(path)}
    return [file for file in files if file in selected_files]


def main() -> int:
    parser = argparse.ArgumentParser(description="Typecheck typeshed's stdlib and third-party stubs with ty.")
    parser.add_argument("paths", nargs="*", type=Path, help="Specific stdlib or third-party stubs to check")
    parser.add_argument("--python", type=Path, help="Python interpreter or environment used to resolve third-party imports")
    parser.add_argument("--python-version", choices=SUPPORTED_VERSIONS, default=SUPPORTED_VERSIONS[0])
    parser.add_argument("--platform", choices=SUPPORTED_PLATFORMS, default="linux")
    args = parser.parse_args()

    stdlib = stdlib_files(args.python_version)
    third_party = third_party_files()
    if args.paths:
        stdlib = _filter_files(stdlib, args.paths, STDLIB_PATH)
        third_party = _filter_files(third_party, args.paths, STUBS_PATH)
    files = [*stdlib, *third_party]
    if not files:
        print("No stubs to check with ty.", flush=True)
        return 0
    command = [
        "ty",
        "check",
        "--config-file",
        str(TS_BASE_PATH / "ty.toml"),
        "--typeshed",
        str(TS_BASE_PATH),
        "--python-version",
        args.python_version,
        "--python-platform",
        args.platform,
        "--output-format",
        "concise",
    ]
    if args.python is not None:
        command.extend(("--python", str(args.python)))

    for path in sorted(STUBS_PATH.iterdir()):
        # requests is obsolete and the typed runtime package provides requests._types.
        if path.is_dir() and path.name != "requests":
            command.extend(("--extra-search-path", str(path)))
    command.extend(map(str, files))

    print(f"Checking {len(files)} stubs with ty ({args.python_version}, {args.platform})...", flush=True)
    # The custom typeshed cannot also be the project root: ty would treat builtins.pyi
    # as project source and panic while constructing its builtins model.
    with tempfile.TemporaryDirectory() as project:
        command[2:2] = ("--project", project)
        return subprocess.run(command, check=False).returncode


if __name__ == "__main__":
    raise SystemExit(main())
