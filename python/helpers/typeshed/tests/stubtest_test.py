#!/usr/bin/env python3
"""Test typeshed using stubtest

stubtest is a script in the mypy project that compares stubs to the actual objects at runtime.
Note that therefore the output of stubtest depends on which Python version it is run with.
In typeshed CI, we run stubtest with each Python minor version from 3.5 through 3.8 inclusive.

We pin the version of mypy / stubtest we use in .travis.yml so changes to those don't break
typeshed CI.

"""

from pathlib import Path
import subprocess
import sys


def run_stubtest(typeshed_dir: Path) -> int:
    whitelist_dir = typeshed_dir / "tests" / "stubtest_whitelists"
    version_whitelist = "py{}{}.txt".format(sys.version_info.major, sys.version_info.minor)

    cmd = [
        sys.executable,
        "-m",
        "mypy.stubtest",
        # Use --ignore-missing-stub, because if someone makes a correct addition, they'll need to
        # also make a whitelist change and if someone makes an incorrect addition, they'll run into
        # false negatives.
        "--ignore-missing-stub",
        "--check-typeshed",
        "--custom-typeshed-dir",
        str(typeshed_dir),
        "--whitelist",
        str(whitelist_dir / "py3_common.txt"),
        "--whitelist",
        str(whitelist_dir / version_whitelist),
    ]
    if sys.version_info < (3, 8):
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
            "NB: We only check positional-only arg accuracy for Python 3.8.\n"
            "If stubtest is complaining about 'unused whitelist entry' after your fix, please "
            "remove the entry from the whitelist file. Note you may have to do this for other "
            "version-specific whitelists as well. Thanks for helping burn the backlog of errors!\n"
            "\nCommand run was: {}\n".format(" ".join(cmd)),
            file=sys.stderr,
        )
        print("stubtest failed", file=sys.stderr)
        return e.returncode
    else:
        print("stubtest succeeded", file=sys.stderr)
        return 0


if __name__ == "__main__":
    sys.exit(run_stubtest(typeshed_dir=Path(".")))
