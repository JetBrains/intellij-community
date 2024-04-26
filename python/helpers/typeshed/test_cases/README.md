## Regression tests for typeshed

This directory contains code samples that act as a regression test for
typeshed's stdlib stubs.

**This directory should *only* contain test cases for functions and classes which
are known to have caused problems in the past, where the stubs are difficult to
get right.** 100% test coverage for typeshed is neither necessary nor
desirable, as it would lead to code duplication. Moreover, typeshed has
multiple other mechanisms for spotting errors in the stubs.

### Where are the third-party test cases?

Not all third-party stubs packages in typeshed have test cases, and not all of
them need test cases. For those that do have test cases, however, the samples
can be found in `@tests/test_cases` subdirectories for each stubs package. For
example, the test cases for `requests` can be found in the
`stubs/requests/@tests/test_cases` directory.

### The purpose of these tests

Different test cases in this directory serve different purposes. For some stubs in
typeshed, the type annotations are complex enough that it's useful to have
sanity checks that test whether a type checker understands the intent of
the annotations correctly. Examples of tests like these are
`stdlib/builtins/check_pow.py` and `stdlib/asyncio/check_gather.py`.

Other test cases, such as the samples for `ExitStack` in `stdlib/check_contextlib.py`
and the samples for `LogRecord` in `stdlib/check_logging.py`, do not relate to
stubs where the annotations are particularly complex, but they *do* relate to
stubs where decisions have been taken that might be slightly unusual. These
test cases serve a different purpose: to check that type checkers do not emit
false-positive errors for idiomatic usage of these classes.

### How the tests work

The code in this directory is not intended to be directly executed. Instead,
type checkers are run on the code, to check that typing errors are
emitted at the correct places.

Some files in this directory simply contain samples of idiomatic Python, which
should not (if the stubs are correct) cause a type checker to emit any errors.

Many test cases also make use of
[`assert_type`](https://docs.python.org/3.11/library/typing.html#typing.assert_type),
a function which allows us to test whether a type checker's inferred type of an
expression is what we'd like it be.

Finally, some tests make use of `# type: ignore` comments (in combination with
mypy's
[`--warn-unused-ignores`](https://mypy.readthedocs.io/en/stable/command_line.html#cmdoption-mypy-warn-unused-ignores)
setting and pyright's
[`reportUnnecessaryTypeIgnoreComment`](https://github.com/microsoft/pyright/blob/main/docs/configuration.md#type-check-diagnostics-settings)
setting) to test instances where a type checker *should* emit some kind of
error, if the stubs are correct. Both settings are enabled by default for the entire
subdirectory.

For more information on using `assert_type` and
`--warn-unused-ignores`/`reportUnnecessaryTypeIgnoreComment` to test type
annotations,
[this page](https://typing.readthedocs.io/en/latest/source/quality.html#testing-using-assert-type-and-warn-unused-ignores)
provides a useful guide.

### Naming convention

Use the same top-level name for the module / package you would like to test.
Use the `check_${thing}.py` naming pattern for individual test files.

By default, test cases go into a file with the same name as the stub file, prefixed with `check_`.
For example: `stdlib/check_contextlib.py`.

If that file becomes too big, we instead create a directory with files named after individual objects being tested.
For example: `stdlib/builtins/check_dict.py`.

### Differences to the rest of typeshed

Unlike the rest of typeshed, this directory largely contains `.py` files. This
is because the purpose of this folder is to test the implications of typeshed
changes for end users, who will mainly be using `.py` files rather than `.pyi`
files.

Another difference to the rest of typeshed
(which stems from the fact that the test-case files are all `.py` files
rather than `.pyi` files)
is that the test cases cannot always use modern syntax for type hints.
While we can use `from __future__ import annotations` to enable the use of
modern typing syntax wherever possible,
type checkers may (correctly) emit errors if PEP 604 syntax or PEP 585 syntax
is used in a runtime context on lower versions of Python. For example:

```python
from __future__ import annotations

from typing_extensions import assert_type

x: str | int  # PEP 604 syntax: okay on Python >=3.7, due to __future__ annotations
assert_type(x, str | int)  # Will fail at runtime on Python <3.10 (use typing.Union instead)

y: dict[str, int]  # PEP 585 syntax: okay on Python >= 3.7, due to __future__ annotations
assert_type(y, dict[str, int])  # Will fail at runtime on Python <3.9 (use typing.Dict instead)
```

### Version-dependent tests

Some tests will only pass on mypy
with a specific Python version passed on the command line to the `tests/regr_test.py` script.
To mark a test-case file as being skippable on lower versions of Python,
append `-py3*` to the filename.
For example, if `foo` is a stdlib feature that's new in Python 3.9,
test cases for `foo` should be put in a file named `test_cases/stdlib/check_foo-py39.py`.
This means that mypy will only run the test case
if `--python-version 3.9`, `--python-version 3.10` or `--python-version 3.11`
is passed on the command line to `tests/regr_test.py`,
but it *won't* run the test case if `--python-version 3.7` or `--python-version 3.8`
is passed on the command line.

However, `if sys.version_info >= (3, target):` is still required for `pyright`
in the test file itself.
Example: [`check_exception_group-py311.py`](https://github.com/python/typeshed/blob/main/test_cases/stdlib/builtins/check_exception_group-py311.py)
