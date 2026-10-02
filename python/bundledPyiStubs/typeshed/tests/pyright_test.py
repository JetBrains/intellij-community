#!/usr/bin/env python3

import os
import shutil
import subprocess
import sys
from pathlib import Path

from ts_utils.utils import parse_requirements, print_command

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
        subprocess.run([npx, "--version"], check=False)
    except OSError:
        print("error running npx; is Node.js installed?", file=sys.stderr)
        sys.exit(1)

    req = parse_requirements()["pyright"]
    spec = str(req.specifier)
    pyright_version = spec[2:]

    # TODO: We're currently using npx to run pyright, instead of calling the
    # version installed into the virtual environment, due to failures on some
    # platforms. https://github.com/python/typeshed/issues/11614
    os.environ["PYRIGHT_PYTHON_FORCE_VERSION"] = pyright_version
    command = [npx, f"pyright@{pyright_version}", *sys.argv[1:]]
    print_command(command)

    ret = subprocess.run(command, check=False).returncode
    sys.exit(ret)


if __name__ == "__main__":
    main()
