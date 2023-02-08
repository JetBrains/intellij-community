## Regression tests for typeshed

This directory contains regression tests for the stubs found elsewhere in the
typeshed repo. Each file contains a number of test cases, all of which should
pass a type checker without error.

**This directory should *only* contain tests for functions and classes which
are known to have caused problems in the past, where the stubs are difficult to
get right.** 100% test coverage for typeshed is neither necessary nor
desirable, as it would lead to code duplication. Moreover, typeshed has
multiple other mechanisms for spotting errors in the stubs.

Unlike the rest of typeshed, this directory largely contains `.py` files. This
is because the purpose of this folder is to test the implications of typeshed
changes for end users.

Another difference to the rest of typeshed is that the test cases in this
directory cannot always use modern syntax for type hints. For example, PEP 604
syntax (unions with a pipe `|` operator) is new in Python 3.10. While this
syntax can be used on older Python versions in a `.pyi` file, code using this
syntax will fail at runtime on Python <=3.9. Since the test cases all use `.py`
extensions, and since the tests need to pass on all Python versions >=3.6, PEP
604 syntax cannot be used in a test case. Use `typing.Union` and
`typing.Optional` instead.
