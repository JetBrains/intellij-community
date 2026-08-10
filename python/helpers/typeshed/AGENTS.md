# typeshed - Python type stub repository

typeshed contains [type stubs](https://typing.python.org/en/latest/spec/distributing.html)
for Python's standard library as well as for some packages available on
[PyPI](https://pypi.org/) (usually called "third-party stubs" in typeshed)
that don't provide their own type annotations.

The standard library stubs get vendored by type checkers. Each third-party
stub package is distributed as a separate package (usually called
`types-<name>`) on PyPI.

## Directory Structure

- `stdlib/` - Python standard library stubs
- `stubs/` - PyPI package stubs, one directory per package
- `scripts`/ - utility scripts
- `tests/` - scripts for various tests, see `tests/README.md`
- `lib/` - utility modules used by multiple scripts

## Running tests

To run all tests:

- Create a new virtual environment (venv), update pip
- Install the dependencies from `requirements-tests.txt` into it
- Run `tests/runtests.py <path>` from the activated venv

`<path>` is either:

- `stdlib/<stub>.pyi`
- `stubs/<package>`

See `tests/README.md` for more information about running tests.

## Pull Requests

When opening pull requests, do the following:

- Follow the guidance from `CONTRIBUTING.md`.
- Run the tests as described above before submitting.
- Don't include tests for .pyi files, unless the situation is complex. See
  `tests/REGRESSION.md`.
- Use a concise PR description:
  - Either link to an issue or describe the problem briefly, never both.
  - Limit the summary of changes to one sentence, unless the PR is complex.
  - Don't include a testing plan.
- Add the name of the agent used to the PR description.
