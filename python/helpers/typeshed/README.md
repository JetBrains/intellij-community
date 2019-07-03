# typeshed

[![Build Status](https://travis-ci.org/python/typeshed.svg?branch=master)](https://travis-ci.org/python/typeshed)
[![Chat at https://gitter.im/python/typing](https://badges.gitter.im/python/typing.svg)](https://gitter.im/python/typing?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Pull Requests Welcome](https://img.shields.io/badge/pull%20requests-welcome-brightgreen.svg)](https://github.com/python/typeshed/blob/master/CONTRIBUTING.md)

## About

Typeshed contains external type annotations for the Python standard library
and Python builtins, as well as third party packages.

This data can e.g. be used for static analysis, type checking or type inference.

For information on how to use `typeshed`, read below.  Information for
contributors can be found in [CONTRIBUTING.md](CONTRIBUTING.md).  **Please read
it before submitting pull requests.**

Typeshed supports Python versions 2.7 and 3.4 and up.

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

Each Python module is represented by a `.pyi` "stub". This is a normal Python
file (i.e., it can be interpreted by Python 3), except all the methods are empty.
Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the types the function has.

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

NOTE: When you're contributing a new stub for a package that you did
not develop, please obtain consent of the package owner (this is
specified in [PEP
484](https://www.python.org/dev/peps/pep-0484/#the-typeshed-repo)).
The best way to obtain consent is to file an issue in the third-party
package's tracker and include the link to a positive response in your PR
for typeshed.

For more information on directory structure and stub versioning, see
[the relevant section of CONTRIBUTING.md](
https://github.com/python/typeshed/blob/master/CONTRIBUTING.md#stub-versioning).

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting pull
requests. If you have questions related to contributing, drop by the [typing Gitter](https://gitter.im/python/typing).

## Running the tests

The tests are automatically run by Travis CI on every PR and push to
the repo.  There are several sets of tests: `tests/mypy_test.py`
runs tests against [mypy](https://github.com/python/mypy/), while
`tests/pytype_test.py` runs tests against
[pytype](https://github.com/google/pytype/).

Both sets of tests are shallow -- they verify that all stubs can be
imported but they don't check whether stubs match their implementation
(in the Python standard library or a third-party package).  Also note
that each set of tests has a blacklist of modules that are not tested
at all.  The blacklists also live in the tests directory.

In addition, you can run `tests/mypy_selftest.py` to run mypy's own
test suite using the typeshed code in your repo. This will sometimes
catch issues with incorrectly typed stubs, but is much slower than the
other tests.

To manually run the mypy tests, you need to have Python 3.5 or higher;
Python 3.6.1 or higher is recommended.

Run:
```
$ python3.6 -m venv .venv3
$ source .venv3/bin/activate
(.venv3)$ pip3 install -r requirements-tests-py3.txt
```
This will install mypy (you need the latest master branch from GitHub),
typed-ast, flake8, and pytype. You can then run mypy, flake8, and pytype tests
by invoking:
```
(.venv3)$ python3 tests/mypy_test.py
...
(.venv3)$ python3 tests/mypy_selftest.py
...
(.venv3)$ flake8
...
(.venv3)$ python3 tests/pytype_test.py
...
```
Note that flake8 only works with Python 3.6 or higher, and that to run the
pytype tests, you will need Python 2.7 and Python 3.6 interpreters. Pytype will
find these automatically if they're in `PATH`, but otherwise you must point to
them with the `--python27-exe` and `--python36-exe` arguments, respectively.

For mypy, if you are in the typeshed repo that is submodule of the
mypy repo (so `..` refers to the mypy repo), there's a shortcut to run
the mypy tests that avoids installing mypy:
```bash
$ PYTHONPATH=../.. python3 tests/mypy_test.py
```
You can mypy tests to a single version by passing `-p2` or `-p3.5` e.g.
```bash
$ PYTHONPATH=../.. python3 tests/mypy_test.py -p3.5
running mypy --python-version 3.5 --strict-optional # with 342 files
```
