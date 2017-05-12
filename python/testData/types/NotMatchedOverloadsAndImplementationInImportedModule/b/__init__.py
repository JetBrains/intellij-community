from typing import overload


@overload
def foo(value: int) -> int:
    pass


@overload
def foo(value: str) -> str:
    pass


def foo(value):
    return None