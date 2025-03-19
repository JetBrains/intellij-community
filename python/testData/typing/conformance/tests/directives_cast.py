"""
Tests the typing.cast call.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#cast

from typing import cast

def find_first_str(a: list[object]) -> str:
    index = next(i for i, x in enumerate(a) if isinstance(x, str))
    return cast(str, a[index])

x: int = cast(int, "not an int")  # No type error

bad1 = cast()  # E: Too few arguments
bad2 = cast(1, "")  # E: Bad first argument type
bad3 = cast(int, "", "")  # E: Too many arguments
