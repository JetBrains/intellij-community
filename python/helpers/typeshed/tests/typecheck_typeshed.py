#!/usr/bin/env python3
"""Run mypy on the "tests" and "scripts" directories."""
from __future__ import annotations

import argparse
import subprocess
import sys
from itertools import product
from typing_extensions import TypeAlias

from utils import colored, print_error

ReturnCode: TypeAlias = int

SUPPORTED_PLATFORMS = ("linux", "darwin", "win32")
SUPPORTED_VERSIONS = ("3.11", "3.10", "3.9")
DIRECTORIES_TO_TEST = ("scripts", "tests")

parser = argparse.ArgumentParser(description="Run mypy on typeshed's own code in the `scripts` and `tests` directories.")
parser.add_argument(
    "dir",
    choices=DIRECTORIES_TO_TEST + ([],),
    nargs="*",
    action="extend",
    help=f"Test only these top-level typeshed directories (defaults to {DIRECTORIES_TO_TEST!r})",
)
parser.add_argument(
    "--platform",
    choices=SUPPORTED_PLATFORMS,
    nargs="*",
    action="extend",
    help="Run mypy for certain OS platforms (defaults to sys.platform)",
)
parser.add_argument(
    "-p",
    "--python-version",
    choices=SUPPORTED_VERSIONS,
    nargs="*",
    action="extend",
    help="Run mypy for certain Python versions (defaults to sys.version_info[:2])",
)


def run_mypy_as_subprocess(directory: str, platform: str, version: str) -> ReturnCode:
    command = [
        sys.executable,
        "-m",
        "mypy",
        directory,
        "--platform",
        platform,
        "--python-version",
        version,
        "--strict",
        "--show-traceback",
        "--show-error-codes",
        "--no-error-summary",
        "--enable-error-code",
        "ignore-without-code",
        "--namespace-packages",
    ]
    if directory == "tests" and platform == "win32":
        command.extend(["--exclude", "tests/pytype_test.py"])
    result = subprocess.run(command, capture_output=True)
    stdout, stderr = result.stdout, result.stderr
    if stderr:
        print_error(stderr.decode())
    if stdout:
        print_error(stdout.decode())
    return result.returncode


def main() -> ReturnCode:
    args = parser.parse_args()
    directories = args.dir or DIRECTORIES_TO_TEST
    platforms = args.platform or [sys.platform]
    versions = args.python_version or [f"3.{sys.version_info[1]}"]

    code = 0

    for directory, platform, version in product(directories, platforms, versions):
        print(f'Running "mypy --platform {platform} --python-version {version}" on the "{directory}" directory...')
        code = max(code, run_mypy_as_subprocess(directory, platform, version))

    if code:
        print_error("Test completed with errors")
    else:
        print(colored("Test completed successfully!", "green"))
    return code


if __name__ == "__main__":
    try:
        code = main()
    except KeyboardInterrupt:
        print_error("\n\n!!!\nTest aborted due to KeyboardInterrupt\n!!!")
        code = 1
    raise SystemExit(code)
