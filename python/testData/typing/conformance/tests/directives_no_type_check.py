"""
Tests the typing.no_type_check decorator.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#no-type-check
# "E?" is used below because support is optional.

from typing import no_type_check

# > The behavior for the ``no_type_check`` decorator when applied to a class is
# > left undefined by the typing spec at this time.

@no_type_check
class ClassA:
    x: int = ""  # E?: No error should be reported


# > If a type checker supports the ``no_type_check`` decorator for functions, it
# > should suppress all type errors for the ``def`` statement and its body including
# > any nested functions or classes. It should also ignore all parameter
# > and return type annotations and treat the function as if it were unannotated.

@no_type_check
def func1(a: int, b: str) -> None:
    c = a + b  # E?: No error should be reported
    return 1  # E?: No error should be reported


func1(b"invalid", b"arguments")  # E?: No error should be reported
# This should still be an error because type checkers should ignore
# annotations, but still check the argument count.
func1()  # E: incorrect arguments for parameters
