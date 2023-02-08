This directory contains several tests:
- `tests/mypy_test.py`
tests typeshed with [mypy](https://github.com/python/mypy/)
- `tests/pytype_test.py` tests typeshed with
[pytype](https://github.com/google/pytype/).
- `tests/pyright_test.py` tests typeshed with
[pyright](https://github.com/microsoft/pyright).
- `tests/check_consistent.py` checks certain files in typeshed remain
consistent with each other.
- `tests/stubtest_stdlib.py` checks standard library stubs against the
objects at runtime.
- `tests/stubtest_third_party.py` checks third-party stubs against the
objects at runtime.

To run the tests, follow the [setup instructions](../CONTRIBUTING.md#preparing-the-environment)
in the `CONTRIBUTING.md` document. In particular, we recommend running with Python 3.8+.

## mypy\_test.py

This test requires Python 3.7+. Run using:
```
(.venv3)$ python3 tests/mypy_test.py
```

The test has four parts. Each part uses mypy with slightly different configuration options:
- Running mypy on the stdlib stubs
- Running mypy on the third-party stubs
- Running mypy `--strict` on the scripts in the `tests` directory
- Running mypy `--strict` on the regression tests in the `test_cases` directory.

When running mypy on the stubs, this test is shallow — it verifies that all stubs can be
imported but doesn't check whether stubs match their implementation
(in the Python standard library or a third-party package).

Run `python tests/mypy_test.py --help` for information on the various configuration options
for this test script.

## pytype\_test.py

This test requires Python 3.7+.
Note: this test cannot be run on Windows
systems unless you are using Windows Subsystem for Linux.

Run using:
```
(.venv3)$ python3 tests/pytype_test.py
```

This test works similarly to `mypy_test.py`, except it uses `pytype`.

## pyright\_test.py

This test requires [Node.js](https://nodejs.org) to be installed. Although
typeshed runs pyright in CI, it does not currently use this script. However,
this script uses the same pyright version and configuration as the CI.
```
(.venv3)$ python3 tests/pyright_test.py                                # Check all files
(.venv3)$ python3 tests/pyright_test.py stdlib/sys.pyi                 # Check one file
(.venv3)$ python3 tests/pyright_test.py -p pyrightconfig.stricter.json # Check with the stricter config.
```

`pyrightconfig.stricter.json` is a stricter configuration that enables additional
checks that would typically fail on incomplete stubs (such as `Unknown` checks).
In typeshed's CI, pyright is run with these configuration settings on a subset of
the stubs in typeshed (including the standard library).

## check\_consistent.py

Run using:
```
python3 tests/check_consistent.py
```

## stubtest\_stdlib.py

This test requires Python 3.6 or higher.
Run using
```
(.venv3)$ python3 tests/stubtest_stdlib.py
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
can add to the allowlists for each affected Python version in
`tests/stubtest_allowlists`. Please file issues for stubtest false positives
at [mypy](https://github.com/python/mypy/issues).

To run stubtest against third party stubs, it's easiest to use stubtest
directly, with
```
(.venv3)$ python3 -m mypy.stubtest \
  --custom-typeshed-dir <path-to-typeshed> \
  <third-party-module>
```
stubtest can also help you find things missing from the stubs.


## stubtest\_third\_party.py

This test requires Python 3.7+.
Run using
```
(.venv3)$ python3 tests/stubtest_third_party.py
```

Similar to `stubtest_stdlib.py`, but tests the third party stubs. By default,
it checks all third-party stubs, but you can provide the distributions to
check on the command line:

```
(.venv3)$ python3 tests/stubtest_third_party.py Pillow toml  # check stubs/Pillow and stubs/toml
```

For each distribution, stubtest ignores definitions listed in a `@tests/stubtest_allowlist.txt` file,
relative to the distribution. Additional packages that are needed to run stubtest for a
distribution can be added to `@tests/requirements-stubtest.txt`.
