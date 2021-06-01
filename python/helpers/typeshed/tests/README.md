This directory contains several tests:
- `tests/mypy_test.py`
tests typeshed with [mypy](https://github.com/python/mypy/)
- `tests/pytype_test.py` tests typeshed with
[pytype](https://github.com/google/pytype/).
- `tests/pyright_test.py` tests typeshed with
[pyright](https://github.com/microsoft/pyright).
- `tests/mypy_test_suite.py` runs a subset of mypy's test suite using this version of
typeshed.
- `tests/check_consistent.py` checks certain files in typeshed remain
consistent with each other.
- `tests/stubtest_test.py` checks stubs against the objects at runtime.

To run the tests, follow the [setup instructions](../CONTRIBUTING.md#preparing-the-environment)
in the `CONTRIBUTING.md` document.

## mypy\_test.py

This test requires Python 3.6 or higher; Python 3.6.1 or higher is recommended.
Run using:
```
(.venv3)$ python3 tests/mypy_test.py
```

This test is shallow — it verifies that all stubs can be
imported but doesn't check whether stubs match their implementation
(in the Python standard library or a third-party package). It has an exclude list of
modules that are not tested at all, which also lives in the tests directory.

You can restrict mypy tests to a single version by passing `-p2` or `-p3.9`:
```bash
(.venv3)$ python3 tests/mypy_test.py -p3.9
```

## pytype\_test.py

This test requires Python 2.7 and Python 3.6. Pytype will
find these automatically if they're in `PATH`.
Run using:
```
(.venv3)$ python3 tests/pytype_test.py
```

This test works similarly to `mypy_test.py`, except it uses `pytype`.

## pyright\_test.py

This test requires [Node.js](https://nodejs.org) to be installed. It is
currently not part of the CI,
but it uses the same pyright version and configuration as the CI.
```
(.venv3)$ python3 tests/pyright_test.py                 # Check all files
(.venv3)$ python3 tests/pyright_test.py stdlib/sys.pyi  # Check one file
```

## mypy\_test\_suite.py

This test requires Python 3.5 or higher; Python 3.6.1 or higher is recommended.
Run using:
```
(.venv3)$ python3 tests/mypy_test_suite.py
```

This test runs mypy's own test suite using the typeshed code in your repo. This
will sometimes catch issues with incorrectly typed stubs, but is much slower
than the other tests.

## check\_consistent.py

Run using:
```
python3 tests/check_consistent.py
```

## stubtest\_test.py

This test requires Python 3.6 or higher.
Run using
```
(.venv3)$ python3 tests/stubtest_test.py
```

This test compares the stdlib stubs against the objects at runtime. Because of
this, the output depends on which version of Python and on what kind of system
it is run.
Thus the easiest way to run this test is via Github Actions on your fork;
if you run it locally, it'll likely complain about system-specific
differences (in e.g, `socket`) that the type system cannot capture.
If you need a specific version of Python to repro a CI failure,
[pyenv](https://github.com/pyenv/pyenv) can help.

Due to its dynamic nature, you may run into false positives. In this case, you
can add to the whitelists for each affected Python version in
`tests/stubtest_whitelists`. Please file issues for stubtest false positives
at [mypy](https://github.com/python/mypy/issues).

To run stubtest against third party stubs, it's easiest to use stubtest
directly, with
```
(.venv3)$ python3 -m mypy.stubtest \
  --custom-typeshed-dir <path-to-typeshed> \
  <third-party-module>
```
stubtest can also help you find things missing from the stubs.
