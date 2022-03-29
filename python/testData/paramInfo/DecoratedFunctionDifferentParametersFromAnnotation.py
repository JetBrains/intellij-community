import functools
from typing import ParamSpec, TypeVar, Callable


def decorator(func) -> Callable[[int], str]:
    def wrapper(*args, **kwargs):
        return ""

    return wrapper


@decorator
def function(input_a: int, input_b: float) -> float:
    return input_a + input_b


print(function(<arg1>))