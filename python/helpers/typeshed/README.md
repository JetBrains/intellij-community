# typeshed

[![Build Status](https://travis-ci.org/python/typeshed.svg?branch=master)](https://travis-ci.org/python/typeshed)
[![Chat at https://gitter.im/python/typing](https://badges.gitter.im/python/typing.svg)](https://gitter.im/python/typing?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Pull Requests Welcome](https://img.shields.io/badge/pull%20requests-welcome-brightgreen.svg)](https://github.com/python/typeshed/blob/master/CONTRIBUTING.md)

## About

Typeshed contains external type annotations for the Python standard library
and Python builtins, as well as third party packages as contributed by
people external to those projects.

This data can e.g. be used for static analysis, type checking or type inference.

For information on how to use `typeshed`, read below.  Information for
contributors can be found in [CONTRIBUTING.md](CONTRIBUTING.md).  **Please read
it before submitting pull requests; do not report issues with annotations to
the project the stubs are for, but instead report them here to typeshed.**

Typeshed supports Python versions 2.7 and 3.5 and up.

## Using

If you're just using mypy (or pytype or PyCharm), as opposed to
developing it, you don't need to interact with the typeshed repo at
all: a copy of typeshed is bundled with mypy.

When you use a checked-out clone of the mypy repo, a copy of typeshed
should be included as a submodule, using

    $ git clone --recurse-submodules https://github.com/python/mypy.git

or

    $ git clone https://github.com/python/mypy.git
    $ cd mypy
    $ git submodule init
    $ git submodule update

and occasionally you will have to repeat the final command (`git
submodule update`) to pull in changes made in the upstream typeshed
repo.

PyCharm and pytype similarly include a copy of typeshed.  The one in
pytype can be updated in the same way if you are working with the
pytype repo.

## Format

Each Python module is represented by a `.pyi` "stub file".  This is a
syntactically valid Python file, although it usually cannot be run by
Python 3 (since forward references don't require string quotes).  All
the methods are empty.

Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the signature of each function or method.

See [PEP 484](http://www.python.org/dev/peps/pep-0484/) for the exact
syntax of the stub files and [CONTRIBUTING.md](CONTRIBUTING.md) for the
coding style used in typeshed.

## Directory structure

### stdlib

This contains stubs for modules the Python standard library -- which
includes pure Python modules, dynamically loaded extension modules,
hard-linked extension modules, and the builtins.

### third_party

Modules that are not shipped with Python but have a type description in Python
go into `third_party`. Since these modules can behave differently for different
versions of Python, `third_party` has version subdirectories, just like
`stdlib`.

For more information on directory structure and stub versioning, see
[the relevant section of CONTRIBUTING.md](
https://github.com/python/typeshed/blob/master/CONTRIBUTING.md#stub-versioning).

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull
requests. If you have questions related to contributing, drop by the [typing Gitter](https://gitter.im/python/typing).

## Running the tests

The tests are automatically run by Travis CI on every PR and push to
the repo. Note that it can be useful to enable Travis CI on your own fork of
typeshed.

There are several tests:
- `tests/mypy_test.py`
runs tests against [mypy](https://github.com/python/mypy/)
- `tests/pytype_test.py` runs tests against
[pytype](https://github.com/google/pytype/).
- `tests/mypy_selftest.py` runs mypy's test suite using this version of
typeshed.
- `tests/check_consistent.py` checks certain files in typeshed remain
consistent with each other.
- `tests/stubtest_test.py` checks stubs against the objects at runtime.
- `flake8` enforces a style guide.

### Setup

Run:
```
$ python3.6 -m venv .venv3
$ source .venv3/bin/activate
(.venv3)$ pip install -U pip
(.venv3)$ pip install -r requirements-tests-py3.txt
```
This will install mypy (you need the latest master branch from GitHub),
typed-ast, flake8 (and plugins), pytype, black and isort.

### mypy_test.py

This test requires Python 3.5 or higher; Python 3.6.1 or higher is recommended.
Run using:`(.venv3)$ python3 tests/mypy_test.py`

This test is shallow — it verifies that all stubs can be
imported but doesn't check whether stubs match their implementation
(in the Python standard library or a third-party package). It has an exclude list of
modules that are not tested at all, which also lives in the tests directory.

If you are in the typeshed repo that is submodule of the
mypy repo (so `..` refers to the mypy repo), there's a shortcut to run
the mypy tests that avoids installing mypy:
```bash
$ PYTHONPATH=../.. python3 tests/mypy_test.py
```
You can restrict mypy tests to a single version by passing `-p2` or `-p3.5`:
```bash
$ PYTHONPATH=../.. python3 tests/mypy_test.py -p3.5
running mypy --python-version 3.5 --strict-optional # with 342 files
```

### pytype_test.py

This test requires Python 2.7 and Python 3.6. Pytype will
find these automatically if they're in `PATH`, but otherwise you must point to
them with the `--python27-exe` and `--python36-exe` arguments, respectively.
Run using: `(.venv3)$ python3 tests/pytype_test.py`

This test works similarly to `mypy_test.py`, except it uses `pytype`.

### mypy_selftest.py

This test requires Python 3.5 or higher; Python 3.6.1 or higher is recommended.
Run using: `(.venv3)$ python3 tests/mypy_selftest.py`

This test runs mypy's own test suite using the typeshed code in your repo. This
will sometimes catch issues with incorrectly typed stubs, but is much slower
than the other tests.

### check_consistent.py

Run using: `python3 tests/check_consistent.py`

### stubtest_test.py

This test requires Python 3.5 or higher.
Run using `(.venv3)$ python3 tests/stubtest_test.py`

This test compares the stdlib stubs against the objects at runtime. Because of
this, the output depends on which version of Python and on what kind of system
it is run.
Thus the easiest way to run this test is by enabling Travis CI on your fork;
if you run it locally, it'll likely complain about system-specific
differences (in e.g, `socket`) that the type system cannot capture.
If you need a specific version of Python to repro a CI failure,
[pyenv](https://github.com/pyenv/pyenv) can help.

Due to its dynamic nature, you may run into false positives. In this case, you
can add to the whitelists for each affected Python version in
`tests/stubtest_whitelists`. Please file issues for stubtest false positives
at [mypy](https://github.com/python/mypy/issues).

To run stubtest against third party stubs, it's easiest to use stubtest
directly, with `(.venv3)$ python3 -m mypy.stubtest --custom-typeshed-dir
<path-to-typeshed> <third-party-module>`.
stubtest can also help you find things missing from the stubs.


### flake8

flake8 requires Python 3.6 or higher. Run using: `(.venv3)$ flake8`

Note typeshed uses the `flake8-pyi` and `flake8-bugbear` plugins.
