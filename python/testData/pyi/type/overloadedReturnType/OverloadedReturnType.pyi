from typing import overload


@overload
def f(x: int) -> int: ...
@overload
def f(x: str) -> str: ...
