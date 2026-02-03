from typing import ParamSpec, Callable

P = ParamSpec("P")


def fo<the_ref>o(x: Callable[P, int], y: Callable[P, int]) -> Callable[P, bool]: ...


def x_y(x: int, y: str) -> int: ...


def y_x(y: int, x: str) -> int: ...


expr = foo(x_y, y_x)