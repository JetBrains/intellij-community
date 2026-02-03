from typing import Callable


def func(x, *args, y, /) -> None: ...


var: [Callable[..., None]] = func