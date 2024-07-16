from typing import overload


@overload
def func(p: int):
    pass


@overload
def func(p1: str, p2: int):
    pass
