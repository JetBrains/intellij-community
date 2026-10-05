"""
Adds the runtime docstrings to the bundled typeshed stdlib.

The typeshed update bot in the TeamCity configuration runs the script after each typeshed update. Quick documentation
then shows the docstrings for every stub definition, also without the interpreter sources and without binary skeletons.

The script runs docstring-adder (https://github.com/astral-sh/docstring-adder), the tool that ty uses for its vendored
typeshed. It runs docstring-adder with Python 3.15 to 3.10, the newest version first. docstring-adder never replaces a
docstring, so the newest text wins, and an older version only fills the branches gated to it.

docstring-adder can only document the modules and the `sys.platform` branches of the current platform. The bot runs
the script on Linux, then on Windows, then on macOS, on the same tree, as ty does in its typeshed sync
(https://github.com/astral-sh/ruff/blob/main/.github/workflows/sync_typeshed.yaml). Each run adds only the docstrings
that are still missing.

The script needs uv 0.12.23 or later, because `uv.lock` has that format.

Usage: uv run --no-project python typeshed_docstrings.py [<typeshed stdlib dir>]
"""
import os
import subprocess
import sys

PYTHON_VERSIONS = ("3.15", "3.14", "3.13", "3.12", "3.11", "3.10")
TOOL_DIR = os.path.dirname(os.path.abspath(__file__))


def main():
    default_stdlib_dir = os.path.join(TOOL_DIR, "..", "..", "bundledPyiStubs", "typeshed", "stdlib")
    stdlib_dir = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else default_stdlib_dir)
    env = dict(os.environ, PYTHONUTF8="1")
    for version in PYTHON_VERSIONS:
        subprocess.run(
            ["uv", "run", "--project", TOOL_DIR, "--locked", "--python", version,
             "add-docstrings", "--stdlib-path", stdlib_dir],
            check=True, env=env,
        )


if __name__ == "__main__":
    main()
