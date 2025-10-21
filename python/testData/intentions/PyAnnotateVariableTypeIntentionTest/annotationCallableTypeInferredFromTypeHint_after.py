from typing import Callable


def foo(c: Callable[[int, str], int]):
    var: [Callable[[int, str], int]] = c