"""
Adds the runtime docstrings to the bundled typeshed stdlib.

The typeshed update bot in the TeamCity configuration runs the script after each typeshed update. Quick documentation
then shows the docstrings for every stub definition, also without the interpreter sources and without binary skeletons.

The script runs docstring-adder (https://github.com/astral-sh/docstring-adder), the tool that ty uses for its vendored
typeshed. It runs docstring-adder with Python versions from `python_versions.py`, the newest version first.
docstring-adder never replaces a docstring, so the newest text wins, and an older version only fills the branches gated to it.

docstring-adder writes each docstring as the runtime gives it, so the lines after the first start at column 0, also
inside a class. The script then formats the stubs with the black version and configuration of typeshed, as ty does:
black indents each docstring to the body of its definition and lays out the code around the new docstrings as typeshed
would. It reads the `pyproject.toml` of typeshed next to the stdlib directory, so the bot checks out that file too.

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

from python_versions import PYTHON_VERSIONS

TOOL_DIR = os.path.dirname(os.path.abspath(__file__))


def main():
    default_stdlib_dir = os.path.join(TOOL_DIR, "..", "..", "bundledPyiStubs", "typeshed", "stdlib")
    stdlib_dir = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else default_stdlib_dir)
    typeshed_config = os.path.join(os.path.dirname(stdlib_dir), "pyproject.toml")
    env = dict(os.environ, PYTHONUTF8="1")
    for version in PYTHON_VERSIONS:
        subprocess.run(
            ["uv", "run", "--project", TOOL_DIR, "--locked", "--python", version,
             "add-docstrings", "--stdlib-path", stdlib_dir],
            check=True, env=env,
        )
    subprocess.run(
        ["uv", "run", "--project", TOOL_DIR, "--locked", "--python", PYTHON_VERSIONS[0],
         "black", "--quiet", "--config", typeshed_config, stdlib_dir],
        check=True, env=env,
    )


if __name__ == "__main__":
    main()
