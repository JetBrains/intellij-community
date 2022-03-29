from typing import Concatenate, Callable, ParamSpec, TypeVar

P = ParamSpec("P")
R = TypeVar("R")


def first_dec_change(f: Callable[P, R]) -> Callable[Concatenate[int, P], list[R]]:
    def inner(aa: int, *args: P.args, **kwargs: P.kwargs) -> list[R]:
        return [f(*args, **kwargs)]

    return inner


def second_dec_same(fun):
    return fun
