"""
Tests the typing.reveal_type function.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#reveal-type

from typing import Any, reveal_type

# > When a static type checker encounters a call to this function, it should
# > emit a diagnostic with the type of the argument.


def func1(a: int | str, b: list[int], c: Any, d: "ForwardReference"):
    reveal_type(a)  # Revealed type is "int | str"
    reveal_type(b)  # Revealed type is "list[int]"
    reveal_type(c)  # Revealed type is "Any"
    reveal_type(d)  # Revealed type is "ForwardReference"

    reveal_type()  # E: not enough arguments
    reveal_type(a, a)  # E: Too many arguments


class ForwardReference:
    pass
