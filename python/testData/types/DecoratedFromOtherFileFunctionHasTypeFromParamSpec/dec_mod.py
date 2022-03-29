import functools
from typing import ParamSpec, TypeVar, Callable


P = ParamSpec("P")
R = TypeVar("R")


def decorator(func: Callable[P, R]) -> Callable[P, R]:
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        print("using_decorator")
        return func(1, 2)
    return wrapper
