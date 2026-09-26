from typing import Callable


def func(x: int, /) -> None:
    pass

var: [Callable[[int], None]] = func