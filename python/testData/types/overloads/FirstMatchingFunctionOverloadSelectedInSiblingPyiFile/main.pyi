from typing import overload


@overload
def func(x: int) -> int:
    pass


@overload
def func(x: str) -> str:
    pass


@overload
def func(x: object) -> object:
    pass
