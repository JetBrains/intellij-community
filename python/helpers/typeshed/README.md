# typeshed

## About

Typeshed contains external type annotations for the Python standard library
and Python builtins, as well as third party packages.

This data can e.g. be used for static analysis, type checking or type inference.

For information on how to use `typeshed`, read below.  Information for
contributors can be found in [CONTRIBUTING.md](CONTRIBUTING.md).  **Please read
it before submitting pull requests.**

## Format

Each Python module is represented by a `.pyi` "stub". This is a normal Python
file (i.e., it can be interpreted by Python 3), except all the methods are empty.
Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the types the function has.

See [PEP 484](http://www.python.org/dev/peps/pep-0484/) for the exact syntax
of the stub files.

## Syntax example

The below is an excerpt from the types for the `datetime` module.

```python
MAXYEAR = ...  # type: int
MINYEAR = ...  # type: int

class date(object):
    def __init__(self, year: int, month: int, day: int) -> None: ...
    @classmethod
    def fromtimestamp(cls, timestamp: int or float) -> date: ...
    @classmethod
    def fromordinal(cls, ordinal: int) -> date: ...
    @classmethod
    def today(self) -> date: ...
    def ctime(self) -> str: ...
    def weekday(self) -> int: ...
```

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
requests.

## Running the tests

The tests are automatically run by Travis CI on every PR and push to
the repo.  There are two separate sets of tests: `tests/mypy_test.py`
runs tests against [mypy](https://github.com/python/mypy/), while
`tests/pytype_tests.py` runs tests against
[pytype](https://github.com/google/pytype/).  The script runtests.sh
runs both sets of tests and flake8 over all .pyi files.

Both sets of tests are shallow -- they verify that all stubs can be
imported but they don't check whether stubs match their implementation
(in the Python standard library or a third-party package).  Also note
that each set of tests has a blacklist of modules that are not tested
at all.  The blacklists also live in the tests directory.

To manually run the mypy tests, you need to have Python 3.5 or higher.
Run:
```
$ python3.5 -m venv .venv3
$ source .venv3/bin/activate
(.venv3)$ pip install -r requirements-tests-py3.txt
```
This will install mypy-lang, typed-ast, and flake8. You can then run
mypy tests and flake8 tests by invoking:
```
(.venv3)$ python tests/mypy_test.py
...
(.venv3)$ flake8
...
```
To run the pytype tests, you need a separate virtual environment with
Python 2.7. Run:
```
$ virtualenv --python=python2.7 .venv2
$ source .venv2/bin/activate
(.venv2)$ pip install -r requirements-tests-py2.txt
```
This will install pytype from its GitHub repo. You can then run pytype
tests by running:
```
(.venv2)$ python tests/pytype_test.py
```

To be able to everything with ``runtests.sh``, copy the ``pytype`` script
from the Python 2 virtualenv to the Python 3 one:
```
$ cp .venv2/bin/pytype .venv3/bin/pytype
$ source .venv3/bin/activate
(.venv3)$ ./runtests.sh
running mypy --python-version 3.6 --strict-optional # with 479 files
running mypy --python-version 3.5 --strict-optional # with 469 files
running mypy --python-version 3.4 --strict-optional # with 469 files
running mypy --python-version 3.3 --strict-optional # with 454 files
running mypy --python-version 3.2 --strict-optional # with 453 files
running mypy --python-version 2.7 --strict-optional # with 502 files
Running pytype tests...
Ran pytype with 244 pyis, got 0 errors.
Running flake8...
flake8 run clean.
(.venv3)$
```

For mypy, if you are in the typeshed repo that is submodule of the
mypy repo (so `..` refers to the mypy repo), there's a shortcut to run
the mypy tests that avoids installing mypy:
```bash
$ PYTHONPATH=.. python3 tests/mypy_test.py
```
You can mypy tests to a single version by passing `-p2` or `-p3.5` e.g.
```bash
$ PYTHONPATH=.. python3 tests/mypy_test.py -p3.5
running mypy --python-version 3.5 --strict-optional # with 342 files
```
