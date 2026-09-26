from typing import Callable, Concatenate, ParamSpec

P = ParamSpec("P")


def bar(x: int, *args: bool) -> int: ...


def ad<the_ref>d(x: Callable[P, int]) -> Callable[Concatenate[str, P], bool]: ...


expression = add(bar)(42, 42, 42)