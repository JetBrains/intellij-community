from typing import Callable, Concatenate, ParamSpec

P = ParamSpec("P")


def bar(x: int, *args: bool) -> int: ...


def rem<the_ref>ove(x: Callable[Concatenate[int, P], int]) -> Callable[P, bool]: ...


expression = remove(bar)(True)