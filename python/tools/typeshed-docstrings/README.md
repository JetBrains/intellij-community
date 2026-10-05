# typeshed-docstrings

Adds the runtime docstrings of the standard library to the bundled typeshed stubs in
`community/python/bundledPyiStubs/typeshed/stdlib`. Quick documentation then shows them for every stub definition, also without
the interpreter sources and without binary skeletons.

`typeshed_docstrings.py` runs [docstring-adder](https://github.com/astral-sh/docstring-adder), the tool that ty uses
for its vendored typeshed.

## Run it after each typeshed update

The typeshed update bot runs the script. To run it by hand, run this command in this directory:

```sh
uv run --no-project python typeshed_docstrings.py
```

The script needs [uv](https://docs.astral.sh/uv/) 0.12.23 or later, because `uv.lock` has that format. It runs
docstring-adder with Python 3.15 to 3.10, the newest version first. docstring-adder never replaces a docstring, so the
newest text wins, and an older version only fills the branches gated to it.

docstring-adder can only document the modules and the `sys.platform` branches of the current platform. The typeshed
update bot runs the script on Linux, then on Windows, then on macOS, on the same tree, as ty does in its
[typeshed sync](https://github.com/astral-sh/ruff/blob/main/.github/workflows/sync_typeshed.yaml). Each run adds
only the docstrings that are still missing. A run on one platform after a fresh typeshed sync adds only the docstrings
of that platform. The next run of the typeshed update bot adds the docstrings of the other platforms.
