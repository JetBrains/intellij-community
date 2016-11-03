# typeshed

## About

Typeshed models function types for the Python standard library
and Python builtins, as well as third party packages.

This data can e.g. be used for static analysis, type checking or type inference.

## Format

Each Python module is represented by a `.pyi` "stub". This is a normal Python
file (i.e., it can be interpreted by Python 3), except all the methods are empty.
Python function annotations ([PEP 3107](https://www.python.org/dev/peps/pep-3107/))
are used to describe the types the function has.
See [PEP 484](http://www.python.org/dev/peps/pep-0484/) for the exact syntax
of the stub files.

## Syntax example

The below is an excerpt from the types for the `datetime` module.

```
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

We're welcoming contributions (pull requests) for type definitions of
third party packages.

NOTE: When you're contributing a new stub for a package that you did
not develop, please obtain consent of the package owner (this is
specified in [PEP
484](https://www.python.org/dev/peps/pep-0484/#the-typeshed-repo)).
The best way to obtain consent is to file an issue in the third party
package's tracker and include the link to a positive response in your PR
for typeshed.

### Version directories

We store stubs for both Python 2 as well as Python 3. We also distinguish
between minor versions (E.g. 3.2 <-> 3.3). To accomplish not having to duplicate
modules that are the same between all minor versions, we have e.g. a top-level
directory 3/ that contains all the stubs for Python 3. More specialized stubs
go into e.g. 3.3/ and supersede the more generic stubs in 3/.
Modules that are the same under both Python 2 and Python 3 go into 2and3/.
Note that the only supported version of Python 2 is 2.7.

### Combining multiple versions in a single file

According to PEP 484, type checkers are expected to understand simple
version and platform checks. So the following syntax is legal in a `pyi`:

```
if sys.version_info >= (3,):
    # Python 3 specific definitions
else:
    # Python 2 specific definitions
```

This can be used for modules in 2and3/ that only have minor changes between
Python 2 and Python 3. If the difference between versions is more drastic, it
can make more sense to have seperate files in 2.x/ and 3.x/.

## Running the tests

The tests are automatically run by Travis-CI on every PR and push to
the repo.  There are two separate sets of tests: `tests/mypy_test.py`
runs tests against [mypy](https://github.com/python/mypy/), while
`tests/pytype_tests.py` runs tests against
[pytype](https://github.com/google/pytype/).  The script runtests.sh
just runs both sets of tests.

Both sets of tests are shallow -- they verify that all stubs can be
imported but they don't check whether stubs match their implementation
(in the Python standard library or a third-party package).  Also note
that each set of tests has a blacklist of modules that are not tested
at all.  The blacklists also live in the tests directory.

To manually run the mypy tests, you need to have Python 3.3 or higher
and `pip3 install mypy-lang`.  To run the pytype tests, you need
Python 2.7 and install pytype from its repo.

For mypy, if you are in the typeshed repo that is submodule of the
mypy repo (so `..` refers to the mypy repo), there's a shortcut to run
the mypy tests that avoids installing mypy:
```
$ PYTHONPATH=.. python3 tests/mypy_tests.py
```
This runs five sets of tests:
```
running mypy --python-version 3.5 --strict-optional # with 342 files
running mypy --python-version 3.4 --strict-optional # with 342 files
running mypy --python-version 3.3 --strict-optional # with 327 files
running mypy --python-version 3.2 --strict-optional # with 326 files
running mypy --python-version 2.7 --strict-optional # with 380 files
```
You can limit it to a single test by passing `-p2` or `-p3.5` e.g.
```
$ PYTHONPATH=.. python3 tests/mypy_tests.py -p3.5
running mypy --python-version 3.5 --strict-optional # with 342 files
```
