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

Another difference to the rest of typeshed is that the test cases in this
directory cannot always use modern syntax for type hints.

For example, PEP 604
syntax (unions with a pipe `|` operator) is new in Python 3.10. While this
syntax can be used on older Python versions in a `.pyi` file, code using this
syntax will fail at runtime on Python <=3.9. Since the test cases all use `.py`
extensions, and since the tests need to pass on all Python versions >=3.7, PEP
604 syntax cannot be used in a test case. Use `typing.Union` and
`typing.Optional` instead.

PEP 585 syntax can also not be used in the `test_cases` directory. Use
`typing.Tuple` instead of `tuple`, `typing.Callable` instead of
`collections.abc.Callable`, and `typing.Match` instead of `re.Match` (etc.).
