"""
Tests the usage of a ParamSpec and its components (P.args, P.kwargs).
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#the-components-of-a-paramspec


from typing import Any, Callable, Concatenate, ParamSpec, assert_type

P = ParamSpec("P")


def puts_p_into_scope1(f: Callable[P, int]) -> None:
    def inner(*args: P.args, **kwargs: P.kwargs) -> None:  # OK
        pass

    def mixed_up(*args: P.kwargs, **kwargs: P.args) -> None:  # E
        pass

    def misplaced1(x: P.args) -> None:  # E
        pass

    def bad_kwargs1(*args: P.args, **kwargs: P.args) -> None:  # E
        pass

    def bad_kwargs2(*args: P.args, **kwargs: Any) -> None:  # E
        pass


def out_of_scope(*args: P.args, **kwargs: P.kwargs) -> None:  # E
    pass


def puts_p_into_scope2(f: Callable[P, int]) -> None:
    stored_args: P.args  # E
    stored_kwargs: P.kwargs  # E

    def just_args(*args: P.args) -> None:  # E
        pass

    def just_kwargs(**kwargs: P.kwargs) -> None:  # E
        pass


def decorator(f: Callable[P, int]) -> Callable[P, None]:
    def foo(*args: P.args, **kwargs: P.kwargs) -> None:
        assert_type(f(*args, **kwargs), int)  # OK

        f(*kwargs, **args)  # E

        f(1, *args, **kwargs)  # E

    return foo  # OK


def add(f: Callable[P, int]) -> Callable[Concatenate[str, P], None]:
    def foo(s: str, *args: P.args, **kwargs: P.kwargs) -> None:  # OK
        pass

    def bar(*args: P.args, s: str, **kwargs: P.kwargs) -> None:  # E
        pass

    return foo  # OK


def remove(f: Callable[Concatenate[int, P], int]) -> Callable[P, None]:
    def foo(*args: P.args, **kwargs: P.kwargs) -> None:
        f(1, *args, **kwargs)  # OK

        f(*args, 1, **kwargs)  # E

        f(*args, **kwargs)  # E

    return foo  # OK


def outer(f: Callable[P, None]) -> Callable[P, None]:
    def foo(x: int, *args: P.args, **kwargs: P.kwargs) -> None:
        f(*args, **kwargs)

    def bar(*args: P.args, **kwargs: P.kwargs) -> None:
        foo(1, *args, **kwargs)  # OK
        foo(x=1, *args, **kwargs)  # E

    return bar


def twice(f: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> int:
    return f(*args, **kwargs) + f(*args, **kwargs)


def a_int_b_str(a: int, b: str) -> int:
    return 0


twice(a_int_b_str, 1, "A")  # OK
twice(a_int_b_str, b="A", a=1)  # OK
twice(a_int_b_str, "A", 1)  # E
