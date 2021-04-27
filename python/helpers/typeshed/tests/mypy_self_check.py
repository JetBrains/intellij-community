#!/usr/bin/env python3
"""Script to run mypy against its own code base."""

import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

REQUIREMENTS_FILE = "requirements-tests-py3.txt"
MYPY_REQUIREMENTS_REGEXPS = [r"^mypy[ =>]", r"^git\+.*/mypy.git\W"]


def determine_mypy_version() -> str:
    with open(REQUIREMENTS_FILE) as f:
        for line in f:
            for regexp in MYPY_REQUIREMENTS_REGEXPS:
                m = re.match(regexp, line)
                if m:
                    return line.strip()
    raise RuntimeError(f"no mypy version found in {REQUIREMENTS_FILE}")


if __name__ == "__main__":
    mypy_version = determine_mypy_version()

    with tempfile.TemporaryDirectory() as tempdir:
        dirpath = Path(tempdir)
        subprocess.run(
            ["git", "clone", "--depth", "1", "git://github.com/python/mypy", dirpath],
            check=True,
        )
        os.environ["MYPYPATH"] = str(dirpath)
        try:
            subprocess.run([sys.executable, "-m", "pip", "install", "-U", mypy_version], check=True)
            subprocess.run([sys.executable, "-m", "pip", "install", "-r", dirpath / "test-requirements.txt"], check=True)
            subprocess.run(
                [
                    "mypy",
                    "--config-file",
                    dirpath / "mypy_self_check.ini",
                    "--custom-typeshed-dir",
                    ".",
                    "-p", "mypy",
                    "-p", "mypyc",
                ],
                check=True,
            )
        except subprocess.CalledProcessError as e:
            print("mypy self test failed", file=sys.stderr)
            sys.exit(e.returncode)
        else:
            print("mypy self test succeeded", file=sys.stderr)
            sys.exit(0)
