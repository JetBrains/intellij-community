from typing import overload


@overload
def foo(value: None) -> None:
    pass


def foo(value):
    return None


@overload
def foo(value: int) -> str:
    pass


def foo(value):
    return None


@overload
def foo(value: str) -> str:
    pass