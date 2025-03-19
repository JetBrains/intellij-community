"""
Tests the typing.assert_type function.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#assert-type

from typing import Annotated, Any, Literal, assert_type


# > When a type checker encounters a call to assert_type(), it should
# > emit an error if the value is not of the specified type.


def func1(
    a: int | str,
    b: list[int],
    c: Any,
    d: "ForwardReference",
    e: Annotated[Literal[4], ""],
):
    assert_type(a, int | str)  # OK
    assert_type(b, list[int])  # OK
    assert_type(c, Any)  # OK
    assert_type(d, "ForwardReference")  # OK
    assert_type(e, Literal[4])  # OK

    assert_type(a, int)  # E: Type mismatch
    assert_type(c, int)  # E: Type mismatch
    assert_type(e, int)  # E: Type mismatch

    assert_type()  # E: not enough arguments
    assert_type("", int)  # E: wrong argument type
    assert_type(a, int | str, a)  # E: too many arguments


class ForwardReference:
    pass
