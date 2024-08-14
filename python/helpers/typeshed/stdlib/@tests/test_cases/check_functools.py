from __future__ import annotations

from functools import cached_property, wraps
from typing import Callable, TypeVar
from typing_extensions import ParamSpec, assert_type

P = ParamSpec("P")
T_co = TypeVar("T_co", covariant=True)


def my_decorator(func: Callable[P, T_co]) -> Callable[P, T_co]:
    @wraps(func)
    def wrapper(*args: P.args, **kwargs: P.kwargs) -> T_co:
        print(args)
        return func(*args, **kwargs)

    # verify that the wrapped function has all these attributes
    wrapper.__annotations__ = func.__annotations__
    wrapper.__doc__ = func.__doc__
    wrapper.__module__ = func.__module__
    wrapper.__name__ = func.__name__
    wrapper.__qualname__ = func.__qualname__
    return wrapper


def check_wraps_function() -> None:
    def wrapped(x: int) -> None: ...
    @wraps(wrapped)
    def identical_wrapper(x: int) -> None: ...
    @wraps(wrapped)
    def other_signature_wrapper(x: str, y: float) -> None: ...

    identical_wrapper(3)
    other_signature_wrapper("parrot", 42.0)


def check_wraps_method() -> None:
    class Wrapped:
        def wrapped(self, x: int) -> None: ...
        @wraps(wrapped)
        def wrapper(self, x: int) -> None: ...

    class Wrapper:  # pyright: ignore[reportUnusedClass]
        @wraps(Wrapped.wrapped)
        def method(self, x: int) -> None: ...

    @wraps(Wrapped.wrapped)
    def func_wrapper(x: int) -> None: ...

    # TODO: The following should work, but currently don't.
    # https://github.com/python/typeshed/issues/10653
    # Wrapped().wrapper(3)
    # Wrapper().method(3)
    func_wrapper(3)


class A:
    def __init__(self, x: int):
        self.x = x

    @cached_property
    def x(self) -> int:
        return 0


assert_type(A(x=1).x, int)


class B:
    @cached_property
    def x(self) -> int:
        return 0


def check_cached_property_settable(x: int) -> None:
    b = B()
    assert_type(b.x, int)
    b.x = x
    assert_type(b.x, int)


# https://github.com/python/typeshed/issues/10048
class Parent: ...


class Child(Parent): ...


class X:
    @cached_property
    def some(self) -> Parent:
        return Parent()


class Y(X):
    @cached_property
    def some(self) -> Child:  # safe override
        return Child()
