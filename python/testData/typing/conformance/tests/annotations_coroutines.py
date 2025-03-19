"""
Tests for annotating coroutines.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/annotations.html#annotating-generator-functions-and-coroutines

# > Coroutines introduced in PEP 492 are annotated with the same syntax as
# > ordinary functions. However, the return type annotation corresponds to
# > the type of await expression, not to the coroutine type.


from typing import Any, Callable, Coroutine, assert_type


async def func1(ignored: int, /) -> str:
    return "spam"


assert_type(func1, Callable[[int], Coroutine[Any, Any, str]])


async def func2() -> None:
    x = await func1(42)
    assert_type(x, str)
