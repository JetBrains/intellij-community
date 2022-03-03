#!/usr/bin/env python3

import shutil
import subprocess
import sys
from pathlib import Path

_PYRIGHT_VERSION = "1.1.192"  # Must match tests.yml.
_WELL_KNOWN_FILE = Path("tests", "pyright_test.py")


def main() -> None:
    if not _WELL_KNOWN_FILE.exists():
        print("pyright_test.py must be run from the typeshed root directory", file=sys.stderr)
        sys.exit(1)

    # subprocess.run on Windows does not look in PATH.
    npx = shutil.which("npx")

    if npx is None:
        print("error finding npx; is Node.js installed?", file=sys.stderr)
        sys.exit(1)

    try:
        subprocess.run([npx, "--version"])
    except OSError:
        print("error running npx; is Node.js installed?", file=sys.stderr)
        sys.exit(1)

    command = [npx, "-p", "pyright@" + _PYRIGHT_VERSION, "pyright"] + sys.argv[1:]

    ret = subprocess.run(command).returncode
    sys.exit(ret)


if __name__ == "__main__":
    main()
