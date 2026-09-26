import functools
from typing import Callable, ParamSpec, TypeVar, Concatenate


P = ParamSpec("P")
R = TypeVar("R")


def decorator(func: Callable[P, R]) -> Callable[Concatenate[str, P], R]:
    @functools.wraps(func)
    def wrapper(input_c: str, input_a: int, input_b: float):
        print("using_decorator")
        return func(input_a, input_b)
    return wrapper


@decorator
def function(input_a: int, input_b: float):
    return input_a + input_b


if __name__ == '__main__':
    function(<arg1>)