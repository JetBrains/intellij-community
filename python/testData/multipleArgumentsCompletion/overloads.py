from typing import overload, Any


@overload
def bar(a: int, b: int) -> None:
    ...


@overload
def bar(c: str, d: str) -> None:
    ...


def bar(*args: Any, **kwargs: Any) -> None:
    ...


def foo():
    a = 1
    b = 2
    c = 3
    d = 4

    bar(<caret>)