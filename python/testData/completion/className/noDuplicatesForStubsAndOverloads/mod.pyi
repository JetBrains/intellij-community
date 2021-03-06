from typing import overload


@overload
def my_func(p: int):
    pass


@overload
def my_func(p1: str, p2: int):
    pass
