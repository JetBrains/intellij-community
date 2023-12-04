#!/usr/bin/env python3
"""Test typeshed's stdlib using stubtest

stubtest is a script in the mypy project that compares stubs to the actual objects at runtime.
Note that therefore the output of stubtest depends on which Python version it is run with.
In typeshed CI, we run stubtest with each currently supported Python minor version.

"""
from __future__ import annotations

import subprocess
import sys
from pathlib import Path


def run_stubtest(typeshed_dir: Path) -> int:
    allowlist_dir = typeshed_dir / "tests" / "stubtest_allowlists"
    version_allowlist = f"py{sys.version_info.major}{sys.version_info.minor}.txt"
    platform_allowlist = f"{sys.platform}.txt"
    combined_allowlist = f"{sys.platform}-py{sys.version_info.major}{sys.version_info.minor}.txt"

    # Note when stubtest imports distutils, it will likely actually import setuptools._distutils
    # This is fine because we don't care about distutils and allowlist all errors from it
    # https://github.com/python/typeshed/pull/10253#discussion_r1216712404
    # https://github.com/python/typeshed/pull/9734
    cmd = [
        sys.executable,
        "-m",
        "mypy.stubtest",
        "--check-typeshed",
        "--custom-typeshed-dir",
        str(typeshed_dir),
        "--allowlist",
        str(allowlist_dir / "py3_common.txt"),
        "--allowlist",
        str(allowlist_dir / version_allowlist),
    ]
    if (allowlist_dir / platform_allowlist).exists():
        cmd += ["--allowlist", str(allowlist_dir / platform_allowlist)]
    if (allowlist_dir / combined_allowlist).exists():
        cmd += ["--allowlist", str(allowlist_dir / combined_allowlist)]
    if sys.version_info < (3, 10):
        # As discussed in https://github.com/python/typeshed/issues/3693, we only aim for
        # positional-only arg accuracy for python 3.10 and above.
        cmd += ["--ignore-positional-only"]
    print(" ".join(cmd), file=sys.stderr)
    try:
        subprocess.run(cmd, check=True)
    except subprocess.CalledProcessError as e:
        print(
            "\nNB: stubtest output depends on the Python version (and system) it is run with. "
            + "See README.md for more details.\n"
            + "NB: We only check positional-only arg accuracy for Python 3.10.\n"
            + f"\nCommand run was: {' '.join(cmd)}\n",
            file=sys.stderr,
        )
        print("\n\n", file=sys.stderr)
        print(f'To fix "unused allowlist" errors, remove the corresponding entries from {allowlist_dir}', file=sys.stderr)
        return e.returncode
    else:
        print("stubtest succeeded", file=sys.stderr)
        return 0


if __name__ == "__main__":
    sys.exit(run_stubtest(typeshed_dir=Path(".")))
