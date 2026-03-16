from typing import Callable


def decorator[**P, R](fn: Callable[P, R]) -> Callable[P, R]:
    return fn