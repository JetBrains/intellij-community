#!/usr/bin/env python3
"""Test typeshed's stdlib using stubtest

stubtest is a script in the mypy project that compares stubs to the actual objects at runtime.
Note that therefore the output of stubtest depends on which Python version it is run with.
In typeshed CI, we run stubtest with each currently supported Python minor version, except 2.7.

"""

import subprocess
import sys
from pathlib import Path


def run_stubtest(typeshed_dir: Path) -> int:
    allowlist_dir = typeshed_dir / "tests" / "stubtest_allowlists"
    version_allowlist = f"py{sys.version_info.major}{sys.version_info.minor}.txt"
    platform_allowlist = f"{sys.platform}.txt"
    combined_allowlist = f"{sys.platform}-py{sys.version_info.major}{sys.version_info.minor}.txt"

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
        # positional-only arg accuracy for the latest Python version.
        cmd += ["--ignore-positional-only"]
    try:
        print(" ".join(cmd), file=sys.stderr)
        subprocess.run(cmd, check=True)
    except subprocess.CalledProcessError as e:
        print(
            "\nNB: stubtest output depends on the Python version (and system) it is run with. "
            "See README.md for more details.\n"
            "NB: We only check positional-only arg accuracy for Python 3.10.\n"
            "\nCommand run was: {}\n".format(" ".join(cmd)),
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
