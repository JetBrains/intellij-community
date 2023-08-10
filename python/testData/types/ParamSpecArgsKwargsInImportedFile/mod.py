from typing import Callable, ParamSpec

P = ParamSpec('P')

def func(c: Callable[P, int], *args: P.args, **kwargs: P.kwargs) -> None:
    ...
