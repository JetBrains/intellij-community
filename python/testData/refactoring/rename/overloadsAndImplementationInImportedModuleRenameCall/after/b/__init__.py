from typing import overload


@overload
def bar(value: None) -> None:
    pass


@overload
def bar(value: int) -> str:
    pass


@overload
def bar(value: str) -> str:
    pass


def bar(value):
    return None