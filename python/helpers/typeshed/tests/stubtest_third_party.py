#!/usr/bin/env python3
"""Test typeshed's third party stubs using stubtest"""

from __future__ import annotations

import argparse
import functools
import subprocess
import sys
import tempfile
import venv
from pathlib import Path
from typing import NoReturn

import tomli
from colors import colored, print_error, print_success_msg


@functools.lru_cache()
def get_mypy_req() -> str:
    with open("requirements-tests.txt") as f:
        return next(line.strip() for line in f if "mypy" in line)


def run_stubtest(dist: Path, *, verbose: bool = False) -> bool:
    with open(dist / "METADATA.toml") as f:
        metadata = dict(tomli.loads(f.read()))

    print(f"{dist.name}... ", end="")

    stubtest_meta = metadata.get("tool", {}).get("stubtest", {})
    if stubtest_meta.get("skip", False):
        print(colored("skipping", "yellow"))
        return True

    with tempfile.TemporaryDirectory() as tmp:
        venv_dir = Path(tmp)
        venv.create(venv_dir, with_pip=True, clear=True)

        pip_exe = str(venv_dir / "bin" / "pip")
        python_exe = str(venv_dir / "bin" / "python")

        dist_version = metadata["version"]
        assert isinstance(dist_version, str)
        dist_req = f"{dist.name}=={dist_version}"

        # If @tests/requirements-stubtest.txt exists, run "pip install" on it.
        req_path = dist / "@tests" / "requirements-stubtest.txt"
        if req_path.exists():
            try:
                pip_cmd = [pip_exe, "install", "-r", str(req_path)]
                subprocess.run(pip_cmd, check=True, capture_output=True)
            except subprocess.CalledProcessError as e:
                print_command_failure("Failed to install requirements", e)
                return False

        # We need stubtest to be able to import the package, so install mypy into the venv
        # Hopefully mypy continues to not need too many dependencies
        # TODO: Maybe find a way to cache these in CI
        dists_to_install = [dist_req, get_mypy_req()]
        dists_to_install.extend(metadata.get("requires", []))
        pip_cmd = [pip_exe, "install"] + dists_to_install
        try:
            subprocess.run(pip_cmd, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            print_command_failure("Failed to install", e)
            return False

        ignore_missing_stub = ["--ignore-missing-stub"] if stubtest_meta.get("ignore_missing_stub", True) else []
        packages_to_check = [d.name for d in dist.iterdir() if d.is_dir() and d.name.isidentifier()]
        modules_to_check = [d.stem for d in dist.iterdir() if d.is_file() and d.suffix == ".pyi"]
        stubtest_cmd = [
            python_exe,
            "-m",
            "mypy.stubtest",
            # Use --custom-typeshed-dir in case we make linked changes to stdlib or _typeshed
            "--custom-typeshed-dir",
            str(dist.parent.parent),
            *ignore_missing_stub,
            *packages_to_check,
            *modules_to_check,
        ]
        allowlist_path = dist / "@tests/stubtest_allowlist.txt"
        if allowlist_path.exists():
            stubtest_cmd.extend(["--allowlist", str(allowlist_path)])

        try:
            subprocess.run(stubtest_cmd, env={"MYPYPATH": str(dist), "MYPY_FORCE_COLOR": "1"}, check=True, capture_output=True)
        except subprocess.CalledProcessError as e:
            print_error("fail")
            print_commands(dist, pip_cmd, stubtest_cmd)
            print_command_output(e)

            print("Ran with the following environment:", file=sys.stderr)
            ret = subprocess.run([pip_exe, "freeze", "--all"], capture_output=True)
            print_command_output(ret)

            if allowlist_path.exists():
                print(
                    f'To fix "unused allowlist" errors, remove the corresponding entries from {allowlist_path}', file=sys.stderr
                )
                print(file=sys.stderr)
            else:
                print(f"Re-running stubtest with --generate-allowlist.\nAdd the following to {allowlist_path}:", file=sys.stderr)
                ret = subprocess.run(stubtest_cmd + ["--generate-allowlist"], env={"MYPYPATH": str(dist)}, capture_output=True)
                print_command_output(ret)

            return False
        else:
            print_success_msg()

    if verbose:
        print_commands(dist, pip_cmd, stubtest_cmd)

    return True


def print_commands(dist: Path, pip_cmd: list[str], stubtest_cmd: list[str]) -> None:
    print(file=sys.stderr)
    print(" ".join(pip_cmd), file=sys.stderr)
    print(f"MYPYPATH={dist}", " ".join(stubtest_cmd), file=sys.stderr)
    print(file=sys.stderr)


def print_command_failure(message: str, e: subprocess.CalledProcessError) -> None:
    print_error("fail")
    print(file=sys.stderr)
    print(message, file=sys.stderr)
    print_command_output(e)


def print_command_output(e: subprocess.CalledProcessError | subprocess.CompletedProcess[bytes]) -> None:
    print(e.stdout.decode(), end="", file=sys.stderr)
    print(e.stderr.decode(), end="", file=sys.stderr)
    print(file=sys.stderr)


def main() -> NoReturn:
    parser = argparse.ArgumentParser()
    parser.add_argument("-v", "--verbose", action="store_true", help="verbose output")
    parser.add_argument("--num-shards", type=int, default=1)
    parser.add_argument("--shard-index", type=int, default=0)
    parser.add_argument("dists", metavar="DISTRIBUTION", type=str, nargs=argparse.ZERO_OR_MORE)
    args = parser.parse_args()

    typeshed_dir = Path(".").resolve()
    if len(args.dists) == 0:
        dists = sorted((typeshed_dir / "stubs").iterdir())
    else:
        dists = [typeshed_dir / "stubs" / d for d in args.dists]

    result = 0
    for i, dist in enumerate(dists):
        if i % args.num_shards != args.shard_index:
            continue
        if not run_stubtest(dist, verbose=args.verbose):
            result = 1
    sys.exit(result)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
